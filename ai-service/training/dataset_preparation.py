from __future__ import annotations

import argparse
import csv
import hashlib
import json
import math
import random
from collections import Counter, defaultdict
from dataclasses import dataclass
from pathlib import Path

import numpy as np
from PIL import Image, ImageOps, UnidentifiedImageError

from issue_types import DATASET_FOLDER_TO_ISSUE_TYPE, EXCLUDED_DATASET_FOLDERS


EXPECTED_SPLITS = ("train", "validate", "test")
EXPECTED_SOURCE_FOLDERS = frozenset(DATASET_FOLDER_TO_ISSUE_TYPE) | EXCLUDED_DATASET_FOLDERS
SUPPORTED_EXTENSIONS = frozenset({".jpg", ".jpeg", ".png"})
DERIVED_SPLIT_RATIOS = {"train": 0.70, "validation": 0.15, "test": 0.15}
DEFAULT_SPLIT_SEED = 2026
_PHASH_SIZE = 32
_PHASH_LOW_FREQUENCY_SIZE = 8


def _dct_matrix(size: int) -> np.ndarray:
    positions = np.arange(size)
    frequencies = np.arange(size)[:, None]
    matrix = np.cos(np.pi * (2 * positions + 1) * frequencies / (2 * size))
    matrix[0] *= 1 / np.sqrt(2)
    return matrix * np.sqrt(2 / size)


_DCT_MATRIX = _dct_matrix(_PHASH_SIZE)


class DatasetPreparationError(RuntimeError):
    """Raised when the raw dataset cannot be prepared safely."""


@dataclass(frozen=True)
class LayoutAudit:
    dataset_root: Path
    source_counts: dict[str, dict[str, int]]

    @property
    def retained_total(self) -> int:
        return sum(
            self.source_counts[split][folder]
            for split in EXPECTED_SPLITS
            for folder in DATASET_FOLDER_TO_ISSUE_TYPE
        )


@dataclass(frozen=True)
class ImageRecord:
    relative_path: str
    original_split: str
    source_folder: str
    label: str
    source_key: str
    sha256: str
    perceptual_hash: str
    difference_hash: str
    width: int
    height: int
    byte_size: int
    image_format: str


@dataclass(frozen=True)
class GroupingResult:
    group_ids: tuple[str, ...]
    group_members: dict[str, tuple[int, ...]]
    exact_duplicate_pairs: int
    source_lineage_pairs: int
    perceptual_duplicate_pairs: int


@dataclass(frozen=True)
class LabelConflictAudit:
    exact_hash_pairs: int
    source_key_pairs: int
    perceptual_pairs: int
    examples: tuple[dict[str, str], ...]

    @property
    def conflict_count(self) -> int:
        return self.exact_hash_pairs + self.source_key_pairs + self.perceptual_pairs


class UnionFind:
    def __init__(self, size: int) -> None:
        self.parent = list(range(size))
        self.rank = [0] * size

    def find(self, index: int) -> int:
        while self.parent[index] != index:
            self.parent[index] = self.parent[self.parent[index]]
            index = self.parent[index]
        return index

    def union(self, left: int, right: int) -> None:
        left_root = self.find(left)
        right_root = self.find(right)
        if left_root == right_root:
            return
        if self.rank[left_root] < self.rank[right_root]:
            left_root, right_root = right_root, left_root
        self.parent[right_root] = left_root
        if self.rank[left_root] == self.rank[right_root]:
            self.rank[left_root] += 1


def validate_dataset_layout(dataset_root: Path) -> LayoutAudit:
    dataset_root = dataset_root.resolve()
    if not dataset_root.is_dir():
        raise DatasetPreparationError(f"Dataset root does not exist: {dataset_root}")

    actual_splits = {path.name for path in dataset_root.iterdir() if path.is_dir()}
    expected_splits = set(EXPECTED_SPLITS)
    if actual_splits != expected_splits:
        raise DatasetPreparationError(
            f"Expected dataset splits {sorted(expected_splits)}, found {sorted(actual_splits)}"
        )

    source_counts: dict[str, dict[str, int]] = {}
    for split in EXPECTED_SPLITS:
        split_root = dataset_root / split
        actual_folders = {path.name for path in split_root.iterdir() if path.is_dir()}
        if actual_folders != EXPECTED_SOURCE_FOLDERS:
            missing = sorted(EXPECTED_SOURCE_FOLDERS - actual_folders)
            unexpected = sorted(actual_folders - EXPECTED_SOURCE_FOLDERS)
            raise DatasetPreparationError(
                f"Split {split} has invalid source folders; missing={missing}, "
                f"unexpected={unexpected}"
            )

        source_counts[split] = {}
        for folder in sorted(EXPECTED_SOURCE_FOLDERS):
            files = [path for path in (split_root / folder).iterdir() if path.is_file()]
            source_counts[split][folder] = len(files)
            if folder in DATASET_FOLDER_TO_ISSUE_TYPE and not files:
                raise DatasetPreparationError(
                    f"Retained class folder is empty: {split}/{folder}"
                )

    return LayoutAudit(dataset_root, source_counts)


def normalized_source_key(path: Path) -> str:
    return path.name.split(".rf.", 1)[0].casefold()


def _bits_to_hex(bits: np.ndarray) -> str:
    value = sum(int(bit) << index for index, bit in enumerate(bits.flatten()))
    return f"{value:016x}"


def fingerprint_image(
        path: Path,
        dataset_root: Path,
        original_split: str,
        source_folder: str,
        label: str) -> ImageRecord:
    if path.suffix.casefold() not in SUPPORTED_EXTENSIONS:
        raise DatasetPreparationError(f"Unsupported image format: {path}")

    content = path.read_bytes()
    if not content:
        raise DatasetPreparationError(f"Image is empty: {path}")

    try:
        with Image.open(path) as image:
            image.load()
            image_format = image.format or "UNKNOWN"
            width, height = image.size
            grayscale = ImageOps.exif_transpose(image).convert("L")
    except (UnidentifiedImageError, OSError) as exception:
        raise DatasetPreparationError(f"Image cannot be decoded: {path}") from exception

    if width <= 0 or height <= 0:
        raise DatasetPreparationError(f"Image has invalid dimensions: {path}")

    phash_pixels = np.asarray(
        grayscale.resize((_PHASH_SIZE, _PHASH_SIZE), Image.Resampling.LANCZOS),
        dtype=np.float32,
    )
    coefficients = _DCT_MATRIX @ phash_pixels @ _DCT_MATRIX.T
    low_frequency = coefficients[
        :_PHASH_LOW_FREQUENCY_SIZE, :_PHASH_LOW_FREQUENCY_SIZE
    ].flatten()
    median = np.median(low_frequency[1:])
    perceptual_hash = _bits_to_hex(low_frequency > median)

    dhash_pixels = np.asarray(
        grayscale.resize((9, 8), Image.Resampling.LANCZOS),
        dtype=np.int16,
    )
    difference_hash = _bits_to_hex(dhash_pixels[:, 1:] > dhash_pixels[:, :-1])

    relative_path = path.relative_to(dataset_root.parent).as_posix()
    return ImageRecord(
        relative_path=relative_path,
        original_split=original_split,
        source_folder=source_folder,
        label=label,
        source_key=normalized_source_key(path),
        sha256=hashlib.sha256(content).hexdigest(),
        perceptual_hash=perceptual_hash,
        difference_hash=difference_hash,
        width=width,
        height=height,
        byte_size=len(content),
        image_format=image_format,
    )


def build_inventory(audit: LayoutAudit) -> list[ImageRecord]:
    records: list[ImageRecord] = []
    for split in EXPECTED_SPLITS:
        for source_folder, label in DATASET_FOLDER_TO_ISSUE_TYPE.items():
            folder = audit.dataset_root / split / source_folder
            for path in sorted(folder.iterdir(), key=lambda item: item.name.casefold()):
                if path.is_file():
                    records.append(fingerprint_image(
                        path, audit.dataset_root, split, source_folder, label
                    ))
    if len(records) != audit.retained_total:
        raise DatasetPreparationError(
            f"Inventory count mismatch: expected {audit.retained_total}, found {len(records)}"
        )
    return records


def hamming_distance(left: str, right: str) -> int:
    return (int(left, 16) ^ int(right, 16)).bit_count()


def _union_matching_values(
        records: list[ImageRecord],
        union_find: UnionFind,
        value_getter) -> int:
    indexes_by_value: dict[tuple[str, str], list[int]] = defaultdict(list)
    for index, record in enumerate(records):
        indexes_by_value[(record.label, value_getter(record))].append(index)

    pair_count = 0
    for indexes in indexes_by_value.values():
        if len(indexes) < 2:
            continue
        pair_count += len(indexes) * (len(indexes) - 1) // 2
        anchor = indexes[0]
        for index in indexes[1:]:
            union_find.union(anchor, index)
    return pair_count


def build_duplicate_groups(records: list[ImageRecord]) -> GroupingResult:
    union_find = UnionFind(len(records))
    exact_duplicate_pairs = _union_matching_values(
        records, union_find, lambda record: record.sha256
    )
    source_lineage_pairs = _union_matching_values(
        records, union_find, lambda record: record.source_key
    )

    indexes_by_label: dict[str, list[int]] = defaultdict(list)
    for index, record in enumerate(records):
        indexes_by_label[record.label].append(index)

    perceptual_duplicate_pairs = 0
    for indexes in indexes_by_label.values():
        for position, left_index in enumerate(indexes):
            left = records[left_index]
            for right_index in indexes[position + 1:]:
                right = records[right_index]
                if left.sha256 == right.sha256:
                    continue
                if (hamming_distance(left.perceptual_hash, right.perceptual_hash) <= 4
                        and hamming_distance(left.difference_hash, right.difference_hash) <= 4):
                    union_find.union(left_index, right_index)
                    perceptual_duplicate_pairs += 1

    indexes_by_root: dict[int, list[int]] = defaultdict(list)
    for index in range(len(records)):
        indexes_by_root[union_find.find(index)].append(index)

    group_ids_by_root: dict[int, str] = {}
    group_members: dict[str, tuple[int, ...]] = {}
    for root, indexes in indexes_by_root.items():
        labels = {records[index].label for index in indexes}
        if len(labels) != 1:
            raise DatasetPreparationError(
                f"Duplicate group unexpectedly spans labels: {sorted(labels)}"
            )
        signature = "\n".join(sorted(records[index].relative_path for index in indexes))
        digest = hashlib.sha256(signature.encode("utf-8")).hexdigest()[:16]
        label_prefix = next(iter(labels)).casefold()
        group_id = f"{label_prefix}-{digest}"
        if group_id in group_members:
            raise DatasetPreparationError(f"Duplicate group identifier collision: {group_id}")
        group_ids_by_root[root] = group_id
        group_members[group_id] = tuple(sorted(indexes))

    group_ids = tuple(group_ids_by_root[union_find.find(index)] for index in range(len(records)))
    return GroupingResult(
        group_ids=group_ids,
        group_members=group_members,
        exact_duplicate_pairs=exact_duplicate_pairs,
        source_lineage_pairs=source_lineage_pairs,
        perceptual_duplicate_pairs=perceptual_duplicate_pairs,
    )


def audit_cross_class_conflicts(records: list[ImageRecord]) -> LabelConflictAudit:
    exact_hash_pairs = 0
    source_key_pairs = 0
    perceptual_pairs = 0
    examples: list[dict[str, str]] = []

    def record_value_conflicts(signal: str, value_getter) -> int:
        nonlocal examples
        indexes_by_value: dict[str, list[int]] = defaultdict(list)
        for index, record in enumerate(records):
            indexes_by_value[value_getter(record)].append(index)
        count = 0
        for indexes in indexes_by_value.values():
            for position, left_index in enumerate(indexes):
                for right_index in indexes[position + 1:]:
                    left = records[left_index]
                    right = records[right_index]
                    if left.label == right.label:
                        continue
                    count += 1
                    if len(examples) < 20:
                        examples.append({
                            "signal": signal,
                            "leftLabel": left.label,
                            "leftPath": left.relative_path,
                            "rightLabel": right.label,
                            "rightPath": right.relative_path,
                        })
        return count

    exact_hash_pairs = record_value_conflicts("SHA256", lambda record: record.sha256)
    source_key_pairs = record_value_conflicts("SOURCE_KEY", lambda record: record.source_key)

    indexes_by_label: dict[str, list[int]] = defaultdict(list)
    for index, record in enumerate(records):
        indexes_by_label[record.label].append(index)
    labels = sorted(indexes_by_label)
    for position, left_label in enumerate(labels):
        for right_label in labels[position + 1:]:
            for left_index in indexes_by_label[left_label]:
                left = records[left_index]
                for right_index in indexes_by_label[right_label]:
                    right = records[right_index]
                    if (hamming_distance(left.perceptual_hash, right.perceptual_hash) <= 4
                            and hamming_distance(left.difference_hash, right.difference_hash) <= 4):
                        perceptual_pairs += 1
                        if len(examples) < 20:
                            examples.append({
                                "signal": "PERCEPTUAL",
                                "leftLabel": left.label,
                                "leftPath": left.relative_path,
                                "rightLabel": right.label,
                                "rightPath": right.relative_path,
                            })

    audit = LabelConflictAudit(
        exact_hash_pairs=exact_hash_pairs,
        source_key_pairs=source_key_pairs,
        perceptual_pairs=perceptual_pairs,
        examples=tuple(examples),
    )
    if audit.conflict_count:
        raise DatasetPreparationError(
            "Cross-class image conflicts require review: "
            f"exact={exact_hash_pairs}, source={source_key_pairs}, "
            f"perceptual={perceptual_pairs}, examples={examples}"
        )
    return audit


def select_representatives(
        records: list[ImageRecord],
        grouping: GroupingResult) -> dict[str, int]:
    representatives: dict[str, int] = {}
    selected_indexes: set[int] = set()
    for group_id, member_indexes in grouping.group_members.items():
        representative = sorted(
            member_indexes,
            key=lambda index: (
                -(records[index].width * records[index].height),
                -records[index].byte_size,
                records[index].relative_path.casefold(),
            ),
        )[0]
        if representative in selected_indexes:
            raise DatasetPreparationError(
                f"Representative selected for more than one group: {representative}"
            )
        representatives[group_id] = representative
        selected_indexes.add(representative)

    if len(representatives) != len(grouping.group_members):
        raise DatasetPreparationError("Not every duplicate group has one representative")
    return representatives


def _split_counts(item_count: int) -> dict[str, int]:
    raw_counts = {
        split: item_count * ratio for split, ratio in DERIVED_SPLIT_RATIOS.items()
    }
    counts = {split: math.floor(value) for split, value in raw_counts.items()}
    remainder = item_count - sum(counts.values())
    split_order = {split: index for index, split in enumerate(DERIVED_SPLIT_RATIOS)}
    by_fraction = sorted(
        DERIVED_SPLIT_RATIOS,
        key=lambda split: (
            -(raw_counts[split] - counts[split]),
            split_order[split],
        ),
    )
    for split in by_fraction[:remainder]:
        counts[split] += 1
    return counts


def assign_derived_splits(
        records: list[ImageRecord],
        representatives: dict[str, int],
        seed: int = DEFAULT_SPLIT_SEED) -> dict[str, str]:
    groups_by_label: dict[str, list[str]] = defaultdict(list)
    for group_id, index in representatives.items():
        groups_by_label[records[index].label].append(group_id)

    assignments: dict[str, str] = {}
    for label in sorted(groups_by_label):
        group_ids = sorted(groups_by_label[label])
        label_seed = int.from_bytes(
            hashlib.sha256(f"{seed}:{label}".encode("utf-8")).digest()[:8],
            byteorder="big",
        )
        random.Random(label_seed).shuffle(group_ids)
        counts = _split_counts(len(group_ids))
        cursor = 0
        for split in DERIVED_SPLIT_RATIOS:
            next_cursor = cursor + counts[split]
            for group_id in group_ids[cursor:next_cursor]:
                assignments[group_id] = split
            cursor = next_cursor
        if cursor != len(group_ids):
            raise DatasetPreparationError(f"Not all {label} groups received a split")

    if set(assignments) != set(representatives):
        raise DatasetPreparationError("Derived split assignment is incomplete")
    return assignments


def assignment_digest(assignments: dict[str, str]) -> str:
    content = "\n".join(
        f"{group_id},{assignments[group_id]}" for group_id in sorted(assignments)
    )
    return hashlib.sha256(content.encode("utf-8")).hexdigest()


def _write_csv(path: Path, fieldnames: tuple[str, ...], rows: list[dict]) -> str:
    temporary_path = path.with_suffix(path.suffix + ".tmp")
    with temporary_path.open("w", newline="", encoding="utf-8") as output:
        writer = csv.DictWriter(output, fieldnames=fieldnames, lineterminator="\n")
        writer.writeheader()
        writer.writerows(rows)
    temporary_path.replace(path)
    return hashlib.sha256(path.read_bytes()).hexdigest()


def prepare_dataset(audit: LayoutAudit, output_dir: Path) -> dict:
    repository_root = audit.dataset_root.parent.resolve()
    output_dir = output_dir.resolve()
    if not output_dir.is_relative_to(repository_root):
        raise DatasetPreparationError(
            f"Output directory must remain inside repository: {output_dir}"
        )
    output_dir.mkdir(parents=True, exist_ok=True)

    records = build_inventory(audit)
    conflicts = audit_cross_class_conflicts(records)
    grouping = build_duplicate_groups(records)
    representatives = select_representatives(records, grouping)
    assignments = assign_derived_splits(records, representatives)

    representative_by_index = {
        index: group_id for group_id, index in representatives.items()
    }
    inventory_rows: list[dict] = []
    manifest_rows: list[dict] = []
    for index, record in enumerate(records):
        group_id = grouping.group_ids[index]
        is_representative = index in representative_by_index
        derived_split = assignments[group_id]
        inventory_rows.append({
            "relative_path": record.relative_path,
            "original_split": record.original_split,
            "source_folder": record.source_folder,
            "label": record.label,
            "source_key": record.source_key,
            "sha256": record.sha256,
            "perceptual_hash": record.perceptual_hash,
            "difference_hash": record.difference_hash,
            "width": record.width,
            "height": record.height,
            "byte_size": record.byte_size,
            "image_format": record.image_format,
            "group_id": group_id,
            "is_representative": "true" if is_representative else "false",
            "derived_split": derived_split,
            "exclusion_reason": "" if is_representative else "DUPLICATE_OR_SOURCE_VARIANT",
        })
        if is_representative:
            manifest_rows.append({
                "relative_path": record.relative_path,
                "label": record.label,
                "derived_split": derived_split,
                "group_id": group_id,
                "original_split": record.original_split,
                "sha256": record.sha256,
                "width": record.width,
                "height": record.height,
                "byte_size": record.byte_size,
            })

    split_order = {split: index for index, split in enumerate(DERIVED_SPLIT_RATIOS)}
    manifest_rows.sort(key=lambda row: (
        split_order[row["derived_split"]], row["label"], row["relative_path"]
    ))

    inventory_path = output_dir / "four_class_inventory.csv"
    manifest_path = output_dir / "four_class_manifest.csv"
    report_path = output_dir / "four_class_dataset_report.json"
    inventory_digest = _write_csv(inventory_path, (
        "relative_path", "original_split", "source_folder", "label", "source_key",
        "sha256", "perceptual_hash", "difference_hash", "width", "height",
        "byte_size", "image_format", "group_id", "is_representative",
        "derived_split", "exclusion_reason",
    ), inventory_rows)
    manifest_digest = _write_csv(manifest_path, (
        "relative_path", "label", "derived_split", "group_id", "original_split",
        "sha256", "width", "height", "byte_size",
    ), manifest_rows)

    class_group_counts = Counter(
        records[index].label for index in representatives.values()
    )
    split_counts: dict[str, Counter] = defaultdict(Counter)
    for group_id, index in representatives.items():
        split_counts[records[index].label][assignments[group_id]] += 1
    largest_group_id, largest_group_members = max(
        grouping.group_members.items(), key=lambda item: len(item[1])
    )
    training_counts = [counts["train"] for counts in split_counts.values()]

    report = {
        "schemaVersion": 1,
        "preparationStatus": "GENERATED_PENDING_VALIDATION",
        "datasetRoot": "archive",
        "supportedLabels": list(DATASET_FOLDER_TO_ISSUE_TYPE.values()),
        "excludedSourceFolders": sorted(EXCLUDED_DATASET_FOLDERS),
        "sourceCounts": audit.source_counts,
        "inventoryImageCount": len(records),
        "uniqueSha256Count": len({record.sha256 for record in records}),
        "duplicateGroupCount": len(grouping.group_members),
        "representativeImageCount": len(representatives),
        "excludedDuplicateOrVariantCount": len(records) - len(representatives),
        "representativeCountByClass": dict(sorted(class_group_counts.items())),
        "representativeCountByClassAndSplit": {
            label: dict(sorted(counts.items()))
            for label, counts in sorted(split_counts.items())
        },
        "splitRatios": DERIVED_SPLIT_RATIOS,
        "splitSeed": DEFAULT_SPLIT_SEED,
        "assignmentDigest": assignment_digest(assignments),
        "inventorySha256": inventory_digest,
        "manifestSha256": manifest_digest,
        "trainingClassImbalanceRatio": round(max(training_counts) / min(training_counts), 6),
        "duplicateSignals": {
            "exactPairs": grouping.exact_duplicate_pairs,
            "sourceLineagePairs": grouping.source_lineage_pairs,
            "perceptualNonExactPairs": grouping.perceptual_duplicate_pairs,
        },
        "largestGroup": {
            "groupId": largest_group_id,
            "label": records[largest_group_members[0]].label,
            "size": len(largest_group_members),
            "sourceKeys": sorted({records[index].source_key for index in largest_group_members}),
        },
        "crossClassConflicts": {
            "exactHashPairs": conflicts.exact_hash_pairs,
            "sourceKeyPairs": conflicts.source_key_pairs,
            "perceptualPairs": conflicts.perceptual_pairs,
        },
    }
    temporary_report = report_path.with_suffix(report_path.suffix + ".tmp")
    temporary_report.write_text(
        json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )
    temporary_report.replace(report_path)
    return report


def _read_csv(path: Path) -> list[dict[str, str]]:
    with path.open(newline="", encoding="utf-8") as source:
        return list(csv.DictReader(source))


def validate_existing_artifacts(audit: LayoutAudit, output_dir: Path) -> dict:
    repository_root = audit.dataset_root.parent.resolve()
    output_dir = output_dir.resolve()
    if not output_dir.is_relative_to(repository_root):
        raise DatasetPreparationError(
            f"Output directory must remain inside repository: {output_dir}"
        )

    inventory_path = output_dir / "four_class_inventory.csv"
    manifest_path = output_dir / "four_class_manifest.csv"
    report_path = output_dir / "four_class_dataset_report.json"
    for path in (inventory_path, manifest_path, report_path):
        if not path.is_file():
            raise DatasetPreparationError(f"Required dataset artifact is missing: {path}")

    report = json.loads(report_path.read_text(encoding="utf-8"))
    inventory_digest = hashlib.sha256(inventory_path.read_bytes()).hexdigest()
    manifest_digest = hashlib.sha256(manifest_path.read_bytes()).hexdigest()
    if inventory_digest != report.get("inventorySha256"):
        raise DatasetPreparationError("Inventory digest does not match the preparation report")
    if manifest_digest != report.get("manifestSha256"):
        raise DatasetPreparationError("Manifest digest does not match the preparation report")

    inventory_rows = _read_csv(inventory_path)
    manifest_rows = _read_csv(manifest_path)
    if len(inventory_rows) != audit.retained_total:
        raise DatasetPreparationError(
            f"Inventory row count mismatch: {len(inventory_rows)}"
        )
    if len(manifest_rows) != report.get("representativeImageCount"):
        raise DatasetPreparationError(
            f"Manifest row count mismatch: {len(manifest_rows)}"
        )

    expected_labels = set(DATASET_FOLDER_TO_ISSUE_TYPE.values())
    expected_splits = set(DERIVED_SPLIT_RATIOS)
    group_splits: dict[str, set[str]] = defaultdict(set)
    sha_splits: dict[str, set[str]] = defaultdict(set)
    source_splits: dict[tuple[str, str], set[str]] = defaultdict(set)
    representative_paths: set[str] = set()
    reconstructed_records: list[ImageRecord] = []
    for row in inventory_rows:
        label = row["label"]
        split = row["derived_split"]
        if label not in expected_labels:
            raise DatasetPreparationError(f"Unsupported inventory label: {label}")
        if split not in expected_splits:
            raise DatasetPreparationError(f"Unsupported derived split: {split}")
        if row["source_folder"] in EXCLUDED_DATASET_FOLDERS:
            raise DatasetPreparationError(
                f"Excluded source folder entered inventory: {row['source_folder']}"
            )

        image_path = (repository_root / row["relative_path"]).resolve()
        if not image_path.is_relative_to(repository_root) or not image_path.is_file():
            raise DatasetPreparationError(f"Unsafe or missing image path: {image_path}")
        try:
            with Image.open(image_path) as image:
                image.load()
        except (UnidentifiedImageError, OSError) as exception:
            raise DatasetPreparationError(
                f"Manifest image cannot be decoded: {image_path}"
            ) from exception

        group_splits[row["group_id"]].add(split)
        sha_splits[row["sha256"]].add(split)
        source_splits[(label, row["source_key"])].add(split)
        if row["is_representative"] == "true":
            if row["relative_path"] in representative_paths:
                raise DatasetPreparationError(
                    f"Representative path is duplicated: {row['relative_path']}"
                )
            representative_paths.add(row["relative_path"])
        elif row["exclusion_reason"] != "DUPLICATE_OR_SOURCE_VARIANT":
            raise DatasetPreparationError(
                f"Non-representative has no exclusion reason: {row['relative_path']}"
            )

        reconstructed_records.append(ImageRecord(
            relative_path=row["relative_path"],
            original_split=row["original_split"],
            source_folder=row["source_folder"],
            label=label,
            source_key=row["source_key"],
            sha256=row["sha256"],
            perceptual_hash=row["perceptual_hash"],
            difference_hash=row["difference_hash"],
            width=int(row["width"]),
            height=int(row["height"]),
            byte_size=int(row["byte_size"]),
            image_format=row["image_format"],
        ))

    if any(len(splits) != 1 for splits in group_splits.values()):
        raise DatasetPreparationError("A duplicate group spans derived splits")
    if any(len(splits) != 1 for splits in sha_splits.values()):
        raise DatasetPreparationError("An exact image hash spans derived splits")
    if any(len(splits) != 1 for splits in source_splits.values()):
        raise DatasetPreparationError("A source identity spans derived splits")

    manifest_paths = {row["relative_path"] for row in manifest_rows}
    if manifest_paths != representative_paths:
        raise DatasetPreparationError(
            "Manifest paths do not exactly match inventory representatives"
        )
    manifest_group_ids = [row["group_id"] for row in manifest_rows]
    if len(manifest_group_ids) != len(set(manifest_group_ids)):
        raise DatasetPreparationError("Manifest contains more than one representative per group")

    class_split_counts: dict[str, Counter] = defaultdict(Counter)
    manifest_assignments: dict[str, str] = {}
    for row in manifest_rows:
        if row["label"] not in expected_labels or row["derived_split"] not in expected_splits:
            raise DatasetPreparationError(f"Invalid manifest row: {row}")
        class_split_counts[row["label"]][row["derived_split"]] += 1
        manifest_assignments[row["group_id"]] = row["derived_split"]
    for label in expected_labels:
        if set(class_split_counts[label]) != expected_splits:
            raise DatasetPreparationError(
                f"Class does not appear in every derived split: {label}"
            )
    if assignment_digest(manifest_assignments) != report.get("assignmentDigest"):
        raise DatasetPreparationError("Manifest split assignment digest does not match")

    audit_cross_class_conflicts(reconstructed_records)
    indexes_by_label: dict[str, list[int]] = defaultdict(list)
    for index, record in enumerate(reconstructed_records):
        indexes_by_label[record.label].append(index)
    perceptual_pairs_checked = 0
    for indexes in indexes_by_label.values():
        for position, left_index in enumerate(indexes):
            left = reconstructed_records[left_index]
            left_split = inventory_rows[left_index]["derived_split"]
            for right_index in indexes[position + 1:]:
                right = reconstructed_records[right_index]
                if (hamming_distance(left.perceptual_hash, right.perceptual_hash) <= 4
                        and hamming_distance(left.difference_hash, right.difference_hash) <= 4):
                    perceptual_pairs_checked += 1
                    if left_split != inventory_rows[right_index]["derived_split"]:
                        raise DatasetPreparationError(
                            "A strict perceptual duplicate pair spans derived splits"
                        )

    report["preparationStatus"] = "READY_FOR_FOUR_CLASS_TRAINING"
    report["validation"] = {
        "decodedImageCount": len(inventory_rows),
        "manifestRowCount": len(manifest_rows),
        "groupLeakageCount": 0,
        "exactHashLeakageCount": 0,
        "sourceKeyLeakageCount": 0,
        "perceptualLeakageCount": 0,
        "perceptualPairsChecked": perceptual_pairs_checked,
        "crossClassConflictCount": 0,
        "classSplitCoverage": "PASS",
        "artifactDigestValidation": "PASS",
    }
    temporary_report = report_path.with_suffix(report_path.suffix + ".tmp")
    temporary_report.write_text(
        json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )
    temporary_report.replace(report_path)
    return report


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Prepare the four-class civic issue dataset")
    parser.add_argument("--dataset-root", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path)
    parser.add_argument("--audit-only", action="store_true")
    parser.add_argument("--inventory-only", action="store_true")
    parser.add_argument("--grouping-only", action="store_true")
    parser.add_argument("--conflicts-only", action="store_true")
    parser.add_argument("--representatives-only", action="store_true")
    parser.add_argument("--splits-only", action="store_true")
    parser.add_argument("--validate-existing", action="store_true")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    audit = validate_dataset_layout(args.dataset_root)
    if args.audit_only:
        print(json.dumps({
            "datasetRoot": audit.dataset_root.as_posix(),
            "retainedTotal": audit.retained_total,
            "sourceCounts": audit.source_counts,
        }, indent=2, sort_keys=True))
        return
    if args.inventory_only:
        records = build_inventory(audit)
        print(json.dumps({
            "retainedTotal": len(records),
            "uniqueSha256": len({record.sha256 for record in records}),
            "formats": sorted({record.image_format for record in records}),
            "zeroByteImages": sum(record.byte_size == 0 for record in records),
        }, indent=2, sort_keys=True))
        return
    if args.grouping_only:
        records = build_inventory(audit)
        grouping = build_duplicate_groups(records)
        group_counts = Counter(records[index].label for index in (
            members[0] for members in grouping.group_members.values()
        ))
        largest_group_id, largest_group_members = max(
            grouping.group_members.items(), key=lambda item: len(item[1])
        )
        print(json.dumps({
            "groupCount": len(grouping.group_members),
            "groupCountByClass": dict(sorted(group_counts.items())),
            "largestGroup": {
                "groupId": largest_group_id,
                "label": records[largest_group_members[0]].label,
                "size": len(largest_group_members),
                "sourceKeyCount": len({
                    records[index].source_key for index in largest_group_members
                }),
                "sourceKeys": sorted({
                    records[index].source_key for index in largest_group_members
                }),
            },
            "exactDuplicatePairs": grouping.exact_duplicate_pairs,
            "sourceLineagePairs": grouping.source_lineage_pairs,
            "perceptualNonExactPairs": grouping.perceptual_duplicate_pairs,
        }, indent=2, sort_keys=True))
        return
    if args.conflicts_only:
        records = build_inventory(audit)
        conflicts = audit_cross_class_conflicts(records)
        print(json.dumps({
            "exactHashPairs": conflicts.exact_hash_pairs,
            "sourceKeyPairs": conflicts.source_key_pairs,
            "perceptualPairs": conflicts.perceptual_pairs,
            "status": "PASS",
        }, indent=2, sort_keys=True))
        return
    if args.representatives_only:
        records = build_inventory(audit)
        grouping = build_duplicate_groups(records)
        representatives = select_representatives(records, grouping)
        selected_counts = Counter(
            records[index].label for index in representatives.values()
        )
        print(json.dumps({
            "inventoryImages": len(records),
            "representativeImages": len(representatives),
            "excludedDuplicateOrVariantImages": len(records) - len(representatives),
            "representativesByClass": dict(sorted(selected_counts.items())),
        }, indent=2, sort_keys=True))
        return
    if args.splits_only:
        records = build_inventory(audit)
        grouping = build_duplicate_groups(records)
        representatives = select_representatives(records, grouping)
        assignments = assign_derived_splits(records, representatives)
        repeated = assign_derived_splits(records, representatives)
        if assignments != repeated:
            raise DatasetPreparationError("Derived split assignment is not deterministic")
        counts: dict[str, Counter] = defaultdict(Counter)
        for group_id, index in representatives.items():
            counts[records[index].label][assignments[group_id]] += 1
        print(json.dumps({
            "assignmentDigest": assignment_digest(assignments),
            "seed": DEFAULT_SPLIT_SEED,
            "ratios": DERIVED_SPLIT_RATIOS,
            "representativeCounts": {
                label: dict(sorted(split_counts.items()))
                for label, split_counts in sorted(counts.items())
            },
        }, indent=2, sort_keys=True))
        return
    if args.validate_existing:
        if args.output_dir is None:
            raise DatasetPreparationError("--output-dir is required for validation")
        print(json.dumps(
            validate_existing_artifacts(audit, args.output_dir),
            indent=2,
            sort_keys=True,
        ))
        return
    if args.output_dir is None:
        raise DatasetPreparationError("--output-dir is required for manifest generation")
    print(json.dumps(prepare_dataset(audit, args.output_dir), indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
