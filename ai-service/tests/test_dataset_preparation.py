from pathlib import Path

import pytest

from training.dataset_preparation import (
    DatasetPreparationError,
    GroupingResult,
    ImageRecord,
    assign_derived_splits,
    assignment_digest,
    audit_cross_class_conflicts,
    build_duplicate_groups,
    normalized_source_key,
    select_representatives,
)


def record(
        name: str,
        label: str = "POTHOLE",
        source_key: str | None = None,
        sha256: str | None = None,
        perceptual_hash: str = "0000000000000000",
        difference_hash: str = "0000000000000000",
        width: int = 100,
        height: int = 100,
        byte_size: int = 1000) -> ImageRecord:
    return ImageRecord(
        relative_path=f"archive/train/source/{name}.jpg",
        original_split="train",
        source_folder="source",
        label=label,
        source_key=source_key or name,
        sha256=sha256 or f"{name:0>64}",
        perceptual_hash=perceptual_hash,
        difference_hash=difference_hash,
        width=width,
        height=height,
        byte_size=byte_size,
        image_format="JPEG",
    )


def test_normalized_source_key_removes_only_roboflow_suffix():
    assert normalized_source_key(
        Path("Example_jpg.rf.0123456789abcdef.jpg")
    ) == "example_jpg"
    assert normalized_source_key(Path("original-name.jpg")) == "original-name.jpg"


def test_duplicate_groups_combine_exact_source_and_perceptual_signals():
    records = [
        record("a", source_key="source-a", sha256="a" * 64),
        record("b", source_key="source-a", sha256="b" * 64,
               perceptual_hash="ffffffffffffffff", difference_hash="ffffffffffffffff"),
        record("c", source_key="source-c", sha256="c" * 64,
               perceptual_hash="fffffffffffffffe", difference_hash="fffffffffffffffe"),
        record("d", source_key="source-d", sha256="d" * 64,
               perceptual_hash="5555555555555555", difference_hash="5555555555555555"),
    ]

    grouping = build_duplicate_groups(records)

    assert grouping.group_ids[0] == grouping.group_ids[1] == grouping.group_ids[2]
    assert grouping.group_ids[3] != grouping.group_ids[0]
    assert len(grouping.group_members) == 2


def test_cross_class_exact_match_is_rejected():
    records = [
        record("a", label="POTHOLE", sha256="a" * 64),
        record("b", label="DAMAGED_SIGN", sha256="a" * 64,
               perceptual_hash="ffffffffffffffff", difference_hash="ffffffffffffffff"),
    ]

    with pytest.raises(DatasetPreparationError, match="Cross-class image conflicts"):
        audit_cross_class_conflicts(records)


def test_representative_prefers_resolution_then_size_then_path():
    records = [
        record("small", width=100, height=100, byte_size=5000),
        record("large-b", width=200, height=200, byte_size=2000),
        record("large-a", width=200, height=200, byte_size=2000),
    ]
    grouping = GroupingResult(
        group_ids=("group", "group", "group"),
        group_members={"group": (0, 1, 2)},
        exact_duplicate_pairs=0,
        source_lineage_pairs=0,
        perceptual_duplicate_pairs=0,
    )

    representatives = select_representatives(records, grouping)

    assert representatives == {"group": 2}


def test_split_assignment_is_deterministic_and_stratified():
    labels = ("DOMESTIC_TRASH", "ILLEGAL_PARKING", "DAMAGED_SIGN", "POTHOLE")
    records = []
    representatives = {}
    for label in labels:
        for number in range(20):
            index = len(records)
            records.append(record(f"{label}-{number}", label=label))
            representatives[f"{label.lower()}-{number}"] = index

    first = assign_derived_splits(records, representatives, seed=2026)
    second = assign_derived_splits(records, representatives, seed=2026)

    assert first == second
    assert assignment_digest(first) == assignment_digest(second)
    for label in labels:
        label_splits = {
            first[group_id] for group_id in representatives if group_id.startswith(label.lower())
        }
        assert label_splits == {"train", "validation", "test"}
