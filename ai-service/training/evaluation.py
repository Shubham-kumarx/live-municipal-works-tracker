from __future__ import annotations

import csv
import json
import statistics
from dataclasses import dataclass
from pathlib import Path

import torch
from torch import nn

from issue_types import (
    CLASS_LABELS,
    CLASS_TO_INDEX,
    DATASET_FOLDER_TO_ISSUE_TYPE,
    INDEX_TO_CLASS,
)
from services.classifier import MODEL_ID, MODEL_REVISION
from training.dataset import ManifestEntry, TrainingManifest
from training.model import ClassificationMetrics, classification_metrics, load_model_artifact


class ModelEvaluationError(RuntimeError):
    """Raised when fixed evaluation inputs do not match the trained model contract."""


@dataclass(frozen=True)
class EvaluationCheckpoint:
    head: nn.Module
    metadata: dict
    history: dict


MISCLASSIFICATION_OBSERVATIONS = {
    "archive/train/Road_Issues_Pothole/"
    "images207_jpg.rf.793529af5b8f6013aed916e07a154d92.jpg": {
        "visualObservation": (
            "The image is a close-up of a vertical cracked surface with a narrow "
            "opening; no road scene or road-surface depression is visible."
        ),
        "interpretation": (
            "The retained POTHOLE ground truth may be visually ambiguous or mislabeled. "
            "The prediction is close: DAMAGED_SIGN and POTHOLE differ by only 0.0160."
        ),
    },
}


@dataclass(frozen=True)
class SamplePrediction:
    relative_path: str
    group_id: str
    actual_label: str
    actual_index: int
    predicted_label: str
    predicted_index: int
    confidence: float
    runner_up_label: str
    runner_up_confidence: float
    confidence_margin: float
    correct: bool
    probabilities: tuple[float, ...]


def build_sample_predictions(
        entries: tuple[ManifestEntry, ...],
        logits: torch.Tensor) -> tuple[SamplePrediction, ...]:
    if logits.ndim != 2 or logits.shape != (len(entries), len(CLASS_LABELS)):
        raise ModelEvaluationError(
            "Prediction logits do not match test rows and canonical classes"
        )
    probabilities = logits.detach().to(device="cpu").softmax(dim=1)
    top_values, top_indexes = probabilities.topk(k=2, dim=1)
    predictions = []
    for row_index, entry in enumerate(entries):
        predicted_index = int(top_indexes[row_index, 0])
        runner_up_index = int(top_indexes[row_index, 1])
        actual_index = CLASS_TO_INDEX[entry.label]
        confidence = float(top_values[row_index, 0])
        runner_up_confidence = float(top_values[row_index, 1])
        predictions.append(SamplePrediction(
            relative_path=entry.relative_path,
            group_id=entry.group_id,
            actual_label=entry.label,
            actual_index=actual_index,
            predicted_label=INDEX_TO_CLASS[predicted_index],
            predicted_index=predicted_index,
            confidence=confidence,
            runner_up_label=INDEX_TO_CLASS[runner_up_index],
            runner_up_confidence=runner_up_confidence,
            confidence_margin=confidence - runner_up_confidence,
            correct=predicted_index == actual_index,
            probabilities=tuple(float(value) for value in probabilities[row_index]),
        ))
    return tuple(sorted(
        predictions,
        key=lambda prediction: (
            prediction.actual_index, prediction.relative_path.casefold()
        ),
    ))


def write_test_predictions(
        predictions: tuple[SamplePrediction, ...], path: Path) -> None:
    fieldnames = (
        "relative_path", "group_id", "actual_label", "actual_index",
        "predicted_label", "predicted_index", "confidence", "runner_up_label",
        "runner_up_confidence", "confidence_margin", "correct",
        *(f"probability_{label}" for label in CLASS_LABELS),
    )
    temporary_path = path.with_suffix(path.suffix + ".tmp")
    path.parent.mkdir(parents=True, exist_ok=True)
    with temporary_path.open("w", newline="", encoding="utf-8") as output:
        writer = csv.DictWriter(output, fieldnames=fieldnames, lineterminator="\n")
        writer.writeheader()
        for prediction in predictions:
            row = {
                "relative_path": prediction.relative_path,
                "group_id": prediction.group_id,
                "actual_label": prediction.actual_label,
                "actual_index": prediction.actual_index,
                "predicted_label": prediction.predicted_label,
                "predicted_index": prediction.predicted_index,
                "confidence": prediction.confidence,
                "runner_up_label": prediction.runner_up_label,
                "runner_up_confidence": prediction.runner_up_confidence,
                "confidence_margin": prediction.confidence_margin,
                "correct": str(prediction.correct).lower(),
            }
            row.update({
                f"probability_{label}": prediction.probabilities[index]
                for index, label in enumerate(CLASS_LABELS)
            })
            writer.writerow(row)
    temporary_path.replace(path)


def calculate_test_metrics(
        predictions: tuple[SamplePrediction, ...]) -> ClassificationMetrics:
    if not predictions:
        raise ModelEvaluationError("Test predictions are empty")
    predicted_indexes = torch.tensor(
        [prediction.predicted_index for prediction in predictions], dtype=torch.long
    )
    actual_indexes = torch.tensor(
        [prediction.actual_index for prediction in predictions], dtype=torch.long
    )
    return classification_metrics(predicted_indexes, actual_indexes)


def confusion_matrix_rows(metrics: ClassificationMetrics) -> list[dict[str, int | str]]:
    if len(metrics.confusion_matrix) != len(CLASS_LABELS):
        raise ModelEvaluationError("Confusion matrix does not have four actual-class rows")
    rows = []
    for actual_index, label in enumerate(CLASS_LABELS):
        values = metrics.confusion_matrix[actual_index]
        if len(values) != len(CLASS_LABELS):
            raise ModelEvaluationError(
                "Confusion matrix does not have four predicted-class columns"
            )
        row: dict[str, int | str] = {"actual_label": label}
        row.update({
            f"predicted_{predicted_label}": int(values[predicted_index])
            for predicted_index, predicted_label in enumerate(CLASS_LABELS)
        })
        row["support"] = sum(values)
        rows.append(row)
    return rows


def write_confusion_matrix(metrics: ClassificationMetrics, path: Path) -> None:
    rows = confusion_matrix_rows(metrics)
    fieldnames = (
        "actual_label",
        *(f"predicted_{label}" for label in CLASS_LABELS),
        "support",
    )
    temporary_path = path.with_suffix(path.suffix + ".tmp")
    path.parent.mkdir(parents=True, exist_ok=True)
    with temporary_path.open("w", newline="", encoding="utf-8") as output:
        writer = csv.DictWriter(output, fieldnames=fieldnames, lineterminator="\n")
        writer.writeheader()
        writer.writerows(rows)
    temporary_path.replace(path)


def classification_report_markdown(evaluation: dict) -> str:
    class_order = evaluation["classOrder"]
    per_class = evaluation["perClass"]
    lines = [
        "# Four-Class Civic Issue Model Evaluation",
        "",
        "This report evaluates the fixed validation-selected checkpoint on the derived",
        "test partition. The test rows did not participate in training or checkpoint",
        "selection, although this partition was previously inspected during Phase 3C.",
        "",
        f"- Checkpoint SHA-256: `{evaluation['classifierHeadSha256']}`",
        f"- Manifest SHA-256: `{evaluation['manifestSha256']}`",
        f"- Test samples: {evaluation['sampleCount']}",
        f"- Correct predictions: {evaluation['correctCount']}",
        f"- Accuracy: {evaluation['accuracy']:.10f}",
        f"- Macro precision: {evaluation['macroPrecision']:.10f}",
        f"- Macro recall: {evaluation['macroRecall']:.10f}",
        f"- Macro-F1: {evaluation['macroF1']:.10f}",
        "",
        "## Per-Class Metrics",
        "",
        "| Class | Precision | Recall | F1 | Support |",
        "|---|---:|---:|---:|---:|",
    ]
    for label in class_order:
        metrics = per_class[label]
        lines.append(
            f"| {label} | {metrics['precision']:.10f} | "
            f"{metrics['recall']:.10f} | {metrics['f1']:.10f} | "
            f"{metrics['support']} |"
        )
    lines.extend([
        "",
        "## Confusion Matrix",
        "",
        "Rows are actual classes and columns are predicted classes.",
        "",
        "| Actual / Predicted | " + " | ".join(class_order) + " |",
        "|---|" + "---:|" * len(class_order),
    ])
    for label, row in zip(class_order, evaluation["confusionMatrix"], strict=True):
        lines.append(f"| {label} | " + " | ".join(str(value) for value in row) + " |")
    lines.extend([
        "",
        "These measurements describe this audited dataset and split only. They do not",
        "establish calibrated confidence or production performance in new locations.",
        "",
    ])
    return "\n".join(lines)


def write_classification_report(evaluation: dict, path: Path) -> None:
    temporary_path = path.with_suffix(path.suffix + ".tmp")
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary_path.write_text(
        classification_report_markdown(evaluation), encoding="utf-8"
    )
    temporary_path.replace(path)


def _confidence_summary(predictions: tuple[SamplePrediction, ...]) -> dict:
    if not predictions:
        return {
            "count": 0,
            "minimum": None,
            "maximum": None,
            "mean": None,
            "median": None,
            "meanMargin": None,
        }
    confidences = [prediction.confidence for prediction in predictions]
    margins = [prediction.confidence_margin for prediction in predictions]
    return {
        "count": len(predictions),
        "minimum": min(confidences),
        "maximum": max(confidences),
        "mean": statistics.fmean(confidences),
        "median": statistics.median(confidences),
        "meanMargin": statistics.fmean(margins),
    }


def analyze_prediction_confidence(
        predictions: tuple[SamplePrediction, ...]) -> dict:
    if not predictions:
        raise ModelEvaluationError("Cannot analyze an empty prediction set")
    correct = tuple(prediction for prediction in predictions if prediction.correct)
    incorrect = tuple(prediction for prediction in predictions if not prediction.correct)
    per_class = {
        label: _confidence_summary(tuple(
            prediction for prediction in predictions
            if prediction.actual_label == label
        ))
        for label in CLASS_LABELS
    }
    bin_definitions = (
        ("[0.00, 0.50)", 0.00, 0.50, False),
        ("[0.50, 0.70)", 0.50, 0.70, False),
        ("[0.70, 0.90)", 0.70, 0.90, False),
        ("[0.90, 1.00]", 0.90, 1.00, True),
    )
    bins = []
    assigned = 0
    for label, lower, upper, include_upper in bin_definitions:
        members = tuple(
            prediction for prediction in predictions
            if prediction.confidence >= lower
            and (prediction.confidence <= upper if include_upper
                 else prediction.confidence < upper)
        )
        assigned += len(members)
        correct_count = sum(prediction.correct for prediction in members)
        bins.append({
            "range": label,
            "count": len(members),
            "correctCount": correct_count,
            "observedAccuracy": correct_count / len(members) if members else None,
            "meanConfidence": (
                statistics.fmean(prediction.confidence for prediction in members)
                if members else None
            ),
        })
    if assigned != len(predictions):
        raise ModelEvaluationError("A confidence score falls outside [0, 1]")
    return {
        "interpretation": (
            "Softmax scores are relative model outputs and are not calibrated "
            "probabilities of real-world correctness."
        ),
        "overall": _confidence_summary(predictions),
        "correct": _confidence_summary(correct),
        "incorrect": _confidence_summary(incorrect),
        "perActualClass": per_class,
        "bins": bins,
    }


def write_json_atomic(value: dict | list, path: Path) -> None:
    temporary_path = path.with_suffix(path.suffix + ".tmp")
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary_path.write_text(
        json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )
    temporary_path.replace(path)


def build_misclassification_records(
        predictions: tuple[SamplePrediction, ...]) -> list[dict]:
    records = []
    for prediction in predictions:
        if prediction.correct:
            continue
        observation = MISCLASSIFICATION_OBSERVATIONS.get(
            prediction.relative_path,
            {
                "visualObservation": "No manual visual observation was recorded.",
                "interpretation": "The error is reported without a causal claim.",
            },
        )
        records.append({
            "relativePath": prediction.relative_path,
            "groupId": prediction.group_id,
            "actualLabel": prediction.actual_label,
            "predictedLabel": prediction.predicted_label,
            "confidence": prediction.confidence,
            "runnerUpLabel": prediction.runner_up_label,
            "runnerUpConfidence": prediction.runner_up_confidence,
            "confidenceMargin": prediction.confidence_margin,
            **observation,
        })
    return records


def validate_class_index_mapping(
        checkpoint: EvaluationCheckpoint,
        manifest: TrainingManifest) -> dict[str, int]:
    if tuple(checkpoint.metadata.get("classLabels", ())) != CLASS_LABELS:
        raise ModelEvaluationError("Checkpoint class order is not canonical")
    if checkpoint.metadata.get("classToIndex") != CLASS_TO_INDEX:
        raise ModelEvaluationError("Checkpoint class indexes are not canonical")

    test_entries = manifest.for_split("test")
    support = {label: 0 for label in CLASS_LABELS}
    group_ids: set[str] = set()
    hashes: set[str] = set()
    for entry in test_entries:
        expected_label = DATASET_FOLDER_TO_ISSUE_TYPE.get(entry.source_folder)
        if expected_label is None or entry.label != expected_label:
            raise ModelEvaluationError(
                f"Test entry has unsupported folder/label mapping: {entry.relative_path}"
            )
        if entry.label not in support:
            raise ModelEvaluationError(f"Unsupported test label: {entry.label}")
        if entry.group_id in group_ids:
            raise ModelEvaluationError("Test split contains a duplicate source group")
        if entry.sha256 in hashes:
            raise ModelEvaluationError("Test split contains a duplicate exact hash")
        group_ids.add(entry.group_id)
        hashes.add(entry.sha256)
        support[entry.label] += 1

    reported = manifest.report.get("representativeCountByClassAndSplit", {})
    expected_support = {
        label: reported.get(label, {}).get("test") for label in CLASS_LABELS
    }
    if support != expected_support:
        raise ModelEvaluationError(
            "Test support does not match the audited dataset report"
        )
    if not all(support.values()):
        raise ModelEvaluationError("Every supported class must appear in the test split")
    return support


def validate_best_checkpoint_metadata(metadata: dict, history: dict) -> None:
    if metadata.get("architecture") != "CLIP_VIT_B32_FROZEN_LINEAR_PROBE":
        raise ModelEvaluationError("Evaluation requires the frozen CLIP linear probe")
    if metadata.get("selectionMetric") != "validationMacroF1":
        raise ModelEvaluationError("Checkpoint was not selected by validation macro-F1")
    if metadata.get("selectionTieBreaker") != "validationLoss":
        raise ModelEvaluationError("Checkpoint validation tie-breaker is inconsistent")
    if metadata.get("bestEpoch") != history.get("bestEpoch"):
        raise ModelEvaluationError("Checkpoint epoch does not match training history")
    if history.get("epochsCompleted", 0) < metadata.get("bestEpoch", 0):
        raise ModelEvaluationError("Training history does not contain the selected epoch")
    selected_rows = [
        row for row in history.get("history", ())
        if row.get("epoch") == metadata.get("bestEpoch")
    ]
    if len(selected_rows) != 1:
        raise ModelEvaluationError("Training history does not uniquely identify the best epoch")
    fine_tuning = metadata.get("fineTuning", {})
    if fine_tuning.get("performed") is not False:
        raise ModelEvaluationError("Unexpected fine-tuned backbone metadata")


def load_best_validation_checkpoint(model_dir: Path) -> EvaluationCheckpoint:
    model_dir = model_dir.resolve()
    history_path = model_dir / "training_history.json"
    if not history_path.is_file():
        raise ModelEvaluationError(f"Training history is missing: {history_path}")
    try:
        history = json.loads(history_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exception:
        raise ModelEvaluationError("Training history is unreadable") from exception
    try:
        head, metadata = load_model_artifact(
            model_dir,
            expected_model_id=MODEL_ID,
            expected_model_revision=MODEL_REVISION,
        )
    except RuntimeError as exception:
        raise ModelEvaluationError(str(exception)) from exception
    validate_best_checkpoint_metadata(metadata, history)
    return EvaluationCheckpoint(head, metadata, history)
