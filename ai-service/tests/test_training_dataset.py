import hashlib
from dataclasses import replace
from pathlib import Path

import pytest

from issue_types import (
    CLASS_LABELS,
    CLASS_TO_INDEX,
    DATASET_FOLDER_TO_ISSUE_TYPE,
    INDEX_TO_CLASS,
)
from training.dataset import (
    ManifestEntry,
    TrainingDatasetError,
    TrainingManifest,
    parse_manifest_entry,
    validate_training_manifest,
)


def manifest_row(
        relative_path: str = "archive/train/Domestic_trash/image.jpg",
        label: str = "DOMESTIC_TRASH") -> dict[str, str]:
    return {
        "relative_path": relative_path,
        "label": label,
        "derived_split": "train",
        "group_id": "domestic-trash-example",
        "original_split": "train",
        "sha256": "a" * 64,
        "width": "224",
        "height": "224",
        "byte_size": "1024",
    }


def test_manifest_entry_accepts_approved_folder_mapping():
    entry = parse_manifest_entry(
        manifest_row(), Path.cwd(), 2, require_file=False
    )

    assert entry.source_folder == "Domestic_trash"
    assert entry.label == "DOMESTIC_TRASH"
    assert entry.derived_split == "train"


def test_class_indexes_have_one_fixed_order():
    assert CLASS_LABELS == (
        "DOMESTIC_TRASH",
        "ILLEGAL_PARKING",
        "DAMAGED_SIGN",
        "POTHOLE",
    )
    assert CLASS_TO_INDEX == {
        "DOMESTIC_TRASH": 0,
        "ILLEGAL_PARKING": 1,
        "DAMAGED_SIGN": 2,
        "POTHOLE": 3,
    }
    assert INDEX_TO_CLASS == {
        0: "DOMESTIC_TRASH",
        1: "ILLEGAL_PARKING",
        2: "DAMAGED_SIGN",
        3: "POTHOLE",
    }
    assert tuple(DATASET_FOLDER_TO_ISSUE_TYPE.values()) == CLASS_LABELS


def test_manifest_entry_rejects_excluded_source_folder():
    row = manifest_row(
        "archive/train/Vandalism_Graffiti/image.jpg", "VANDALISM"
    )

    with pytest.raises(TrainingDatasetError, match="Excluded source folder"):
        parse_manifest_entry(row, Path.cwd(), 2, require_file=False)


def test_manifest_entry_rejects_folder_label_mismatch():
    row = manifest_row(label="POTHOLE")

    with pytest.raises(TrainingDatasetError, match="expected DOMESTIC_TRASH"):
        parse_manifest_entry(row, Path.cwd(), 2, require_file=False)


@pytest.mark.parametrize(
    "relative_path",
    [
        "../outside.jpg",
        "archive/train/unknown/image.jpg",
        "archive/unknown/Domestic_trash/image.jpg",
    ],
)
def test_manifest_entry_rejects_unsafe_or_unknown_paths(relative_path):
    with pytest.raises(TrainingDatasetError):
        parse_manifest_entry(
            manifest_row(relative_path), Path.cwd(), 2, require_file=False
        )


def valid_training_manifest() -> TrainingManifest:
    entries = []
    source_folders = {
        "DOMESTIC_TRASH": "Domestic_trash",
        "ILLEGAL_PARKING": "Parking_Issues_Illegal_Parking",
        "DAMAGED_SIGN": "Road_Issues_Damaged_Sign",
        "POTHOLE": "Road_Issues_Pothole",
    }
    for label in CLASS_LABELS:
        for split in ("train", "validation", "test"):
            name = f"{label.casefold()}-{split}"
            entries.append(ManifestEntry(
                relative_path=f"archive/train/{source_folders[label]}/{name}.jpg",
                absolute_path=Path.cwd() / f"{name}.jpg",
                label=label,
                derived_split=split,
                group_id=name,
                original_split="train",
                source_folder=source_folders[label],
                sha256=hashlib.sha256(name.encode()).hexdigest(),
                width=224,
                height=224,
                byte_size=1024,
            ))
    assignment_content = "\n".join(
        f"{entry.group_id},{entry.derived_split}"
        for entry in sorted(entries, key=lambda item: item.group_id)
    )
    counts = {
        label: {"test": 1, "train": 1, "validation": 1}
        for label in CLASS_LABELS
    }
    report = {
        "preparationStatus": "READY_FOR_FOUR_CLASS_TRAINING",
        "supportedLabels": list(CLASS_LABELS),
        "excludedSourceFolders": [
            "Infrastructure_Damage_Concrete", "Vandalism_Graffiti"
        ],
        "representativeImageCount": len(entries),
        "representativeCountByClassAndSplit": counts,
        "assignmentDigest": hashlib.sha256(
            assignment_content.encode("utf-8")
        ).hexdigest(),
        "validation": {
            "groupLeakageCount": 0,
            "exactHashLeakageCount": 0,
            "sourceKeyLeakageCount": 0,
            "perceptualLeakageCount": 0,
            "crossClassConflictCount": 0,
            "classSplitCoverage": "PASS",
            "artifactDigestValidation": "PASS",
        },
    }
    return TrainingManifest(tuple(entries), "manifest-digest", report)


def test_training_manifest_accepts_complete_leakage_safe_splits():
    counts = validate_training_manifest(valid_training_manifest())

    assert counts["POTHOLE"] == {"test": 1, "train": 1, "validation": 1}


def test_training_manifest_rejects_non_ready_report():
    manifest = valid_training_manifest()
    invalid_report = dict(manifest.report, preparationStatus="GENERATED_PENDING_VALIDATION")

    with pytest.raises(TrainingDatasetError, match="not READY"):
        validate_training_manifest(replace(manifest, report=invalid_report))


def test_training_manifest_rejects_cross_split_hash():
    manifest = valid_training_manifest()
    entries = list(manifest.entries)
    entries[1] = replace(entries[1], sha256=entries[0].sha256)

    with pytest.raises(TrainingDatasetError, match="hash spans derived splits"):
        validate_training_manifest(replace(manifest, entries=tuple(entries)))
