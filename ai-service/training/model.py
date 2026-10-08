from __future__ import annotations

import hashlib
import json
from dataclasses import dataclass
from pathlib import Path

import torch
from torch import nn

from issue_types import CLASS_LABELS


EMBEDDING_SIZE = 512


class FourClassHead(nn.Module):
    """Linear probe over normalized CLIP image embeddings."""

    def __init__(self) -> None:
        super().__init__()
        self.classifier = nn.Linear(EMBEDDING_SIZE, len(CLASS_LABELS))

    def forward(self, embeddings: torch.Tensor) -> torch.Tensor:
        if embeddings.ndim != 2 or embeddings.shape[1] != EMBEDDING_SIZE:
            raise ValueError(
                f"Expected embeddings shaped [batch, {EMBEDDING_SIZE}], "
                f"received {tuple(embeddings.shape)}"
            )
        return self.classifier(embeddings)


@dataclass(frozen=True)
class ClassificationMetrics:
    accuracy: float
    macro_precision: float
    macro_recall: float
    macro_f1: float
    per_class: dict[str, dict[str, float | int]]
    confusion_matrix: list[list[int]]


def save_head_weights(head: FourClassHead, path: Path) -> str:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary_path = path.with_suffix(path.suffix + ".tmp")
    state = {
        name: tensor.detach().to(device="cpu").contiguous()
        for name, tensor in head.state_dict().items()
    }
    torch.save(state, temporary_path)
    temporary_path.replace(path)
    return hashlib.sha256(path.read_bytes()).hexdigest()


def load_head_weights(path: Path, device: str | torch.device = "cpu") -> FourClassHead:
    try:
        state = torch.load(path, map_location=device, weights_only=True)
    except (OSError, RuntimeError, ValueError) as exception:
        raise RuntimeError(f"Classifier head cannot be loaded: {path}") from exception
    head = FourClassHead().to(device)
    try:
        head.load_state_dict(state, strict=True)
    except (RuntimeError, TypeError) as exception:
        raise RuntimeError(f"Classifier head is incompatible: {path}") from exception
    head.eval()
    return head


def load_model_artifact(
        model_dir: Path,
        *,
        expected_model_id: str,
        expected_model_revision: str,
        device: str | torch.device = "cpu") -> tuple[FourClassHead, dict]:
    model_dir = model_dir.resolve()
    metadata_path = model_dir / "model_metadata.json"
    head_path = model_dir / "classifier_head.pt"
    for path in (metadata_path, head_path):
        if not path.is_file():
            raise RuntimeError(f"Required trained-model artifact is missing: {path}")
    try:
        metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exception:
        raise RuntimeError("Trained-model metadata is unreadable") from exception

    required_values = {
        "schemaVersion": 1,
        "architecture": "CLIP_VIT_B32_FROZEN_LINEAR_PROBE",
        "baseModelId": expected_model_id,
        "baseModelRevision": expected_model_revision,
        "embeddingSize": EMBEDDING_SIZE,
        "inputImageSize": 224,
    }
    for field, expected in required_values.items():
        if metadata.get(field) != expected:
            raise RuntimeError(
                f"Trained-model metadata {field} does not match the runtime"
            )
    if tuple(metadata.get("classLabels", ())) != CLASS_LABELS:
        raise RuntimeError("Trained-model class order does not match the runtime")
    expected_indexes = {label: index for index, label in enumerate(CLASS_LABELS)}
    if metadata.get("classToIndex") != expected_indexes:
        raise RuntimeError("Trained-model class indexes do not match the runtime")

    actual_digest = hashlib.sha256(head_path.read_bytes()).hexdigest()
    if actual_digest != metadata.get("classifierHeadSha256"):
        raise RuntimeError("Classifier-head SHA-256 does not match its metadata")
    return load_head_weights(head_path, device), metadata


def classification_metrics(
        predictions: torch.Tensor,
        targets: torch.Tensor) -> ClassificationMetrics:
    predictions = predictions.detach().to(dtype=torch.long, device="cpu").flatten()
    targets = targets.detach().to(dtype=torch.long, device="cpu").flatten()
    if predictions.shape != targets.shape or predictions.numel() == 0:
        raise ValueError("Predictions and targets must be non-empty and have equal shape")
    class_count = len(CLASS_LABELS)
    if ((predictions < 0) | (predictions >= class_count)).any():
        raise ValueError("Prediction index is outside the canonical class order")
    if ((targets < 0) | (targets >= class_count)).any():
        raise ValueError("Target index is outside the canonical class order")

    confusion = torch.zeros((class_count, class_count), dtype=torch.long)
    for target, prediction in zip(targets.tolist(), predictions.tolist(), strict=True):
        confusion[target, prediction] += 1

    per_class: dict[str, dict[str, float | int]] = {}
    precisions: list[float] = []
    recalls: list[float] = []
    f1_scores: list[float] = []
    for index, label in enumerate(CLASS_LABELS):
        true_positive = int(confusion[index, index])
        support = int(confusion[index, :].sum())
        predicted_count = int(confusion[:, index].sum())
        precision = true_positive / predicted_count if predicted_count else 0.0
        recall = true_positive / support if support else 0.0
        f1 = (
            2 * precision * recall / (precision + recall)
            if precision + recall else 0.0
        )
        precisions.append(precision)
        recalls.append(recall)
        f1_scores.append(f1)
        per_class[label] = {
            "precision": precision,
            "recall": recall,
            "f1": f1,
            "support": support,
        }

    return ClassificationMetrics(
        accuracy=float(confusion.diagonal().sum()) / int(confusion.sum()),
        macro_precision=sum(precisions) / class_count,
        macro_recall=sum(recalls) / class_count,
        macro_f1=sum(f1_scores) / class_count,
        per_class=per_class,
        confusion_matrix=confusion.tolist(),
    )
