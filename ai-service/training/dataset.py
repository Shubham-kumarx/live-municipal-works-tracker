from __future__ import annotations

import csv
import hashlib
import json
from collections import Counter, defaultdict
from dataclasses import dataclass
from pathlib import Path

from PIL import Image, UnidentifiedImageError

from issue_types import (
    CLASS_LABELS,
    DATASET_FOLDER_TO_ISSUE_TYPE,
    EXCLUDED_DATASET_FOLDERS,
)


EXPECTED_MANIFEST_FIELDS = (
    "relative_path",
    "label",
    "derived_split",
    "group_id",
    "original_split",
    "sha256",
    "width",
    "height",
    "byte_size",
)
EXPECTED_SOURCE_SPLITS = frozenset({"train", "validate", "test"})
EXPECTED_DERIVED_SPLITS = frozenset({"train", "validation", "test"})


class TrainingDatasetError(RuntimeError):
    """Raised when training inputs do not match the audited dataset contract."""


@dataclass(frozen=True)
class ManifestEntry:
    relative_path: str
    absolute_path: Path
    label: str
    derived_split: str
    group_id: str
    original_split: str
    source_folder: str
    sha256: str
    width: int
    height: int
    byte_size: int


@dataclass(frozen=True)
class TrainingManifest:
    entries: tuple[ManifestEntry, ...]
    manifest_sha256: str
    report: dict

    def for_split(self, split: str) -> tuple[ManifestEntry, ...]:
        if split not in EXPECTED_DERIVED_SPLITS:
            raise TrainingDatasetError(f"Unsupported derived split: {split}")
        return tuple(entry for entry in self.entries if entry.derived_split == split)


def _assignment_digest(entries: tuple[ManifestEntry, ...]) -> str:
    content = "\n".join(
        f"{entry.group_id},{entry.derived_split}"
        for entry in sorted(entries, key=lambda item: item.group_id)
    )
    return hashlib.sha256(content.encode("utf-8")).hexdigest()


def validate_training_manifest(manifest: TrainingManifest) -> dict[str, dict[str, int]]:
    report = manifest.report
    if report.get("preparationStatus") != "READY_FOR_FOUR_CLASS_TRAINING":
        raise TrainingDatasetError(
            "Dataset report is not READY_FOR_FOUR_CLASS_TRAINING"
        )
    if tuple(report.get("supportedLabels", ())) != CLASS_LABELS:
        raise TrainingDatasetError(
            "Dataset report class order does not match the training class order"
        )
    if set(report.get("excludedSourceFolders", ())) != EXCLUDED_DATASET_FOLDERS:
        raise TrainingDatasetError(
            "Dataset report excluded-folder policy does not match the application"
        )
    if len(manifest.entries) != report.get("representativeImageCount"):
        raise TrainingDatasetError(
            "Manifest row count does not match the dataset report"
        )

    paths = [entry.relative_path for entry in manifest.entries]
    group_ids = [entry.group_id for entry in manifest.entries]
    if len(paths) != len(set(paths)):
        raise TrainingDatasetError("Training manifest contains duplicate image paths")
    if len(group_ids) != len(set(group_ids)):
        raise TrainingDatasetError(
            "Training manifest contains more than one representative per source group"
        )

    splits_by_sha: dict[str, set[str]] = defaultdict(set)
    counts: dict[str, Counter] = defaultdict(Counter)
    for entry in manifest.entries:
        counts[entry.label][entry.derived_split] += 1
        splits_by_sha[entry.sha256].add(entry.derived_split)
    if any(len(splits) != 1 for splits in splits_by_sha.values()):
        raise TrainingDatasetError("An exact image hash spans derived splits")

    actual_counts = {
        label: {split: counts[label][split] for split in sorted(EXPECTED_DERIVED_SPLITS)}
        for label in CLASS_LABELS
    }
    for label in CLASS_LABELS:
        if set(counts[label]) != EXPECTED_DERIVED_SPLITS:
            raise TrainingDatasetError(
                f"Class does not appear in every derived split: {label}"
            )

    reported_counts = report.get("representativeCountByClassAndSplit")
    if actual_counts != reported_counts:
        raise TrainingDatasetError(
            "Manifest class/split counts do not match the dataset report"
        )
    if _assignment_digest(manifest.entries) != report.get("assignmentDigest"):
        raise TrainingDatasetError(
            "Manifest split assignment digest does not match the dataset report"
        )

    validation = report.get("validation", {})
    required_zero_counts = (
        "groupLeakageCount",
        "exactHashLeakageCount",
        "sourceKeyLeakageCount",
        "perceptualLeakageCount",
        "crossClassConflictCount",
    )
    if any(validation.get(field) != 0 for field in required_zero_counts):
        raise TrainingDatasetError(
            "Dataset report does not contain zero-leakage validation evidence"
        )
    if validation.get("classSplitCoverage") != "PASS":
        raise TrainingDatasetError("Dataset report class/split coverage did not pass")
    if validation.get("artifactDigestValidation") != "PASS":
        raise TrainingDatasetError("Dataset report artifact validation did not pass")
    return actual_counts


def _required_text(row: dict[str, str], field: str, row_number: int) -> str:
    value = row.get(field, "").strip()
    if not value:
        raise TrainingDatasetError(
            f"Manifest row {row_number} has no value for {field}"
        )
    return value


def _positive_integer(row: dict[str, str], field: str, row_number: int) -> int:
    value = _required_text(row, field, row_number)
    try:
        parsed = int(value)
    except ValueError as exception:
        raise TrainingDatasetError(
            f"Manifest row {row_number} has invalid {field}: {value}"
        ) from exception
    if parsed <= 0:
        raise TrainingDatasetError(
            f"Manifest row {row_number} has non-positive {field}: {parsed}"
        )
    return parsed


def parse_manifest_entry(
        row: dict[str, str],
        repository_root: Path,
        row_number: int,
        *,
        require_file: bool = True) -> ManifestEntry:
    relative_path = _required_text(row, "relative_path", row_number)
    relative = Path(relative_path)
    if relative.is_absolute() or ".." in relative.parts:
        raise TrainingDatasetError(
            f"Manifest row {row_number} has unsafe path: {relative_path}"
        )

    parts = relative.parts
    if len(parts) != 4 or parts[0] != "archive":
        raise TrainingDatasetError(
            f"Manifest row {row_number} has unexpected dataset path: {relative_path}"
        )
    original_split, source_folder = parts[1], parts[2]
    if original_split not in EXPECTED_SOURCE_SPLITS:
        raise TrainingDatasetError(
            f"Manifest row {row_number} has unsupported source split: {original_split}"
        )
    if source_folder in EXCLUDED_DATASET_FOLDERS:
        raise TrainingDatasetError(
            f"Excluded source folder entered training manifest: {source_folder}"
        )
    expected_label = DATASET_FOLDER_TO_ISSUE_TYPE.get(source_folder)
    if expected_label is None:
        raise TrainingDatasetError(
            f"Manifest row {row_number} has unsupported source folder: {source_folder}"
        )

    label = _required_text(row, "label", row_number)
    if label != expected_label:
        raise TrainingDatasetError(
            f"Manifest row {row_number} maps {source_folder} to {label}, "
            f"expected {expected_label}"
        )
    recorded_original_split = _required_text(row, "original_split", row_number)
    if recorded_original_split != original_split:
        raise TrainingDatasetError(
            f"Manifest row {row_number} source split does not match its path"
        )

    derived_split = _required_text(row, "derived_split", row_number)
    if derived_split not in EXPECTED_DERIVED_SPLITS:
        raise TrainingDatasetError(
            f"Manifest row {row_number} has unsupported derived split: {derived_split}"
        )

    repository_root = repository_root.resolve()
    absolute_path = (repository_root / relative).resolve()
    if not absolute_path.is_relative_to(repository_root):
        raise TrainingDatasetError(
            f"Manifest row {row_number} resolves outside the repository"
        )
    if require_file and not absolute_path.is_file():
        raise TrainingDatasetError(
            f"Manifest row {row_number} image does not exist: {relative_path}"
        )

    sha256 = _required_text(row, "sha256", row_number).casefold()
    if len(sha256) != 64 or any(character not in "0123456789abcdef" for character in sha256):
        raise TrainingDatasetError(
            f"Manifest row {row_number} has invalid SHA-256"
        )

    return ManifestEntry(
        relative_path=relative.as_posix(),
        absolute_path=absolute_path,
        label=label,
        derived_split=derived_split,
        group_id=_required_text(row, "group_id", row_number),
        original_split=original_split,
        source_folder=source_folder,
        sha256=sha256,
        width=_positive_integer(row, "width", row_number),
        height=_positive_integer(row, "height", row_number),
        byte_size=_positive_integer(row, "byte_size", row_number),
    )


def load_training_manifest(
        manifest_path: Path,
        report_path: Path,
        repository_root: Path,
        *,
        require_files: bool = True) -> TrainingManifest:
    manifest_path = manifest_path.resolve()
    report_path = report_path.resolve()
    repository_root = repository_root.resolve()
    for path, description in (
            (manifest_path, "training manifest"),
            (report_path, "dataset preparation report")):
        if not path.is_file():
            raise TrainingDatasetError(f"Missing {description}: {path}")
        if not path.is_relative_to(repository_root):
            raise TrainingDatasetError(
                f"{description.capitalize()} must be inside the repository: {path}"
            )

    try:
        report = json.loads(report_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exception:
        raise TrainingDatasetError("Dataset preparation report is unreadable") from exception

    manifest_digest = hashlib.sha256(manifest_path.read_bytes()).hexdigest()
    if manifest_digest != report.get("manifestSha256"):
        raise TrainingDatasetError(
            "Training manifest SHA-256 does not match the preparation report"
        )

    with manifest_path.open(newline="", encoding="utf-8") as source:
        reader = csv.DictReader(source)
        if tuple(reader.fieldnames or ()) != EXPECTED_MANIFEST_FIELDS:
            raise TrainingDatasetError(
                f"Unexpected manifest columns: {reader.fieldnames}"
            )
        entries = tuple(
            parse_manifest_entry(
                row,
                repository_root,
                row_number,
                require_file=require_files,
            )
            for row_number, row in enumerate(reader, start=2)
        )
    if not entries:
        raise TrainingDatasetError("Training manifest is empty")
    manifest = TrainingManifest(entries, manifest_digest, report)
    validate_training_manifest(manifest)
    return manifest


def decode_image(entry: ManifestEntry) -> Image.Image:
    try:
        with Image.open(entry.absolute_path) as image:
            image.load()
            return image.convert("RGB")
    except (UnidentifiedImageError, OSError) as exception:
        raise TrainingDatasetError(
            f"Training image cannot be decoded: {entry.relative_path}"
        ) from exception
