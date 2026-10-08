from copy import deepcopy
import hashlib
from pathlib import Path

import pytest
import torch

from issue_types import CLASS_LABELS, CLASS_TO_INDEX
from training.dataset import ManifestEntry, TrainingManifest
from training.evaluation import (
    EvaluationCheckpoint,
    ModelEvaluationError,
    analyze_prediction_confidence,
    build_misclassification_records,
    build_sample_predictions,
    calculate_test_metrics,
    classification_report_markdown,
    confusion_matrix_rows,
    load_best_validation_checkpoint,
    validate_class_index_mapping,
    validate_best_checkpoint_metadata,
)


def metadata() -> dict:
    return {
        "architecture": "CLIP_VIT_B32_FROZEN_LINEAR_PROBE",
        "selectionMetric": "validationMacroF1",
        "selectionTieBreaker": "validationLoss",
        "bestEpoch": 5,
        "fineTuning": {"performed": False},
    }


def history() -> dict:
    return {
        "bestEpoch": 5,
        "epochsCompleted": 5,
        "history": [{"epoch": number} for number in range(1, 6)],
    }


def test_best_checkpoint_metadata_accepts_validation_selected_linear_probe():
    validate_best_checkpoint_metadata(metadata(), history())


@pytest.mark.parametrize(
    ("field", "value", "message"),
    [
        ("architecture", "OTHER", "frozen CLIP linear probe"),
        ("selectionMetric", "trainingAccuracy", "validation macro-F1"),
        ("selectionTieBreaker", "finalEpoch", "tie-breaker"),
        ("bestEpoch", 4, "does not match training history"),
    ],
)
def test_best_checkpoint_metadata_rejects_inconsistent_selection(field, value, message):
    invalid = deepcopy(metadata())
    invalid[field] = value

    with pytest.raises(ModelEvaluationError, match=message):
        validate_best_checkpoint_metadata(invalid, history())


def test_actual_best_validation_checkpoint_loads_read_only():
    checkpoint = load_best_validation_checkpoint(Path("models/four-class-clip"))

    assert checkpoint.metadata["bestEpoch"] == 50
    assert checkpoint.history["bestEpoch"] == 50
    assert checkpoint.metadata["fineTuning"]["performed"] is False


def evaluation_manifest() -> TrainingManifest:
    folders = {
        "DOMESTIC_TRASH": "Domestic_trash",
        "ILLEGAL_PARKING": "Parking_Issues_Illegal_Parking",
        "DAMAGED_SIGN": "Road_Issues_Damaged_Sign",
        "POTHOLE": "Road_Issues_Pothole",
    }
    entries = []
    for label in CLASS_LABELS:
        name = f"{label.casefold()}-test"
        entries.append(ManifestEntry(
            relative_path=f"archive/test/{folders[label]}/{name}.jpg",
            absolute_path=Path(name),
            label=label,
            derived_split="test",
            group_id=name,
            original_split="test",
            source_folder=folders[label],
            sha256=hashlib.sha256(name.encode()).hexdigest(),
            width=224,
            height=224,
            byte_size=1024,
        ))
    report = {
        "representativeCountByClassAndSplit": {
            label: {"test": 1} for label in CLASS_LABELS
        }
    }
    return TrainingManifest(tuple(entries), "manifest", report)


def evaluation_checkpoint() -> EvaluationCheckpoint:
    return EvaluationCheckpoint(
        head=None,
        metadata={
            "classLabels": list(CLASS_LABELS),
            "classToIndex": CLASS_TO_INDEX,
        },
        history={},
    )


def test_class_mapping_matches_checkpoint_and_test_manifest():
    support = validate_class_index_mapping(
        evaluation_checkpoint(), evaluation_manifest()
    )

    assert support == {label: 1 for label in CLASS_LABELS}


def test_class_mapping_rejects_reordered_checkpoint_labels():
    checkpoint = evaluation_checkpoint()
    checkpoint.metadata["classLabels"] = list(reversed(CLASS_LABELS))

    with pytest.raises(ModelEvaluationError, match="class order"):
        validate_class_index_mapping(checkpoint, evaluation_manifest())


def test_sample_predictions_preserve_probabilities_and_sort_by_class():
    entries = tuple(reversed(evaluation_manifest().entries))
    logits = torch.tensor([
        [0.0, 0.0, 0.0, 4.0],
        [0.0, 0.0, 4.0, 0.0],
        [0.0, 4.0, 0.0, 0.0],
        [4.0, 0.0, 0.0, 0.0],
    ])

    predictions = build_sample_predictions(entries, logits)

    assert [prediction.actual_label for prediction in predictions] == list(CLASS_LABELS)
    assert all(prediction.correct for prediction in predictions)
    assert all(len(prediction.probabilities) == 4 for prediction in predictions)
    assert all(prediction.confidence_margin > 0 for prediction in predictions)


def test_sample_predictions_reject_wrong_logit_shape():
    with pytest.raises(ModelEvaluationError, match="logits"):
        build_sample_predictions(
            evaluation_manifest().entries, torch.zeros((4, 5))
        )


def test_accuracy_is_calculated_from_saved_prediction_pairs():
    entries = evaluation_manifest().entries
    logits = torch.tensor([
        [4.0, 0.0, 0.0, 0.0],
        [0.0, 4.0, 0.0, 0.0],
        [0.0, 0.0, 4.0, 0.0],
        [0.0, 0.0, 4.0, 1.0],
    ])
    predictions = build_sample_predictions(entries, logits)

    metrics = calculate_test_metrics(predictions)

    assert metrics.accuracy == 0.75
    assert sum(
        metrics.confusion_matrix[index][index] for index in range(4)
    ) == 3


def test_per_class_precision_uses_confusion_matrix_columns():
    predictions = build_sample_predictions(
        evaluation_manifest().entries,
        torch.tensor([
            [4.0, 0.0, 0.0, 0.0],
            [0.0, 4.0, 0.0, 0.0],
            [0.0, 0.0, 4.0, 0.0],
            [0.0, 0.0, 4.0, 1.0],
        ]),
    )

    per_class = calculate_test_metrics(predictions).per_class

    assert per_class["DOMESTIC_TRASH"]["precision"] == 1.0
    assert per_class["ILLEGAL_PARKING"]["precision"] == 1.0
    assert per_class["DAMAGED_SIGN"]["precision"] == 0.5
    assert per_class["POTHOLE"]["precision"] == 0.0


def test_per_class_recall_uses_confusion_matrix_rows():
    predictions = build_sample_predictions(
        evaluation_manifest().entries,
        torch.tensor([
            [4.0, 0.0, 0.0, 0.0],
            [0.0, 4.0, 0.0, 0.0],
            [0.0, 0.0, 4.0, 0.0],
            [0.0, 0.0, 4.0, 1.0],
        ]),
    )

    per_class = calculate_test_metrics(predictions).per_class

    assert per_class["DOMESTIC_TRASH"]["recall"] == 1.0
    assert per_class["ILLEGAL_PARKING"]["recall"] == 1.0
    assert per_class["DAMAGED_SIGN"]["recall"] == 1.0
    assert per_class["POTHOLE"]["recall"] == 0.0


def test_per_class_f1_is_harmonic_mean_of_precision_and_recall():
    predictions = build_sample_predictions(
        evaluation_manifest().entries,
        torch.tensor([
            [4.0, 0.0, 0.0, 0.0],
            [0.0, 4.0, 0.0, 0.0],
            [0.0, 0.0, 4.0, 0.0],
            [0.0, 0.0, 4.0, 1.0],
        ]),
    )

    per_class = calculate_test_metrics(predictions).per_class

    assert per_class["DOMESTIC_TRASH"]["f1"] == 1.0
    assert per_class["ILLEGAL_PARKING"]["f1"] == 1.0
    assert per_class["DAMAGED_SIGN"]["f1"] == pytest.approx(2 / 3)
    assert per_class["POTHOLE"]["f1"] == 0.0


def test_macro_metrics_weight_all_four_classes_equally():
    predictions = build_sample_predictions(
        evaluation_manifest().entries,
        torch.tensor([
            [4.0, 0.0, 0.0, 0.0],
            [0.0, 4.0, 0.0, 0.0],
            [0.0, 0.0, 4.0, 0.0],
            [0.0, 0.0, 4.0, 1.0],
        ]),
    )

    metrics = calculate_test_metrics(predictions)

    assert metrics.macro_precision == pytest.approx(0.625)
    assert metrics.macro_recall == pytest.approx(0.75)
    assert metrics.macro_f1 == pytest.approx(2 / 3)


def test_confusion_matrix_rows_have_explicit_axes_and_support():
    predictions = build_sample_predictions(
        evaluation_manifest().entries,
        torch.tensor([
            [4.0, 0.0, 0.0, 0.0],
            [0.0, 4.0, 0.0, 0.0],
            [0.0, 0.0, 4.0, 0.0],
            [0.0, 0.0, 4.0, 1.0],
        ]),
    )

    rows = confusion_matrix_rows(calculate_test_metrics(predictions))

    assert len(rows) == 4
    assert sum(int(row["support"]) for row in rows) == 4
    assert rows[3]["actual_label"] == "POTHOLE"
    assert rows[3]["predicted_DAMAGED_SIGN"] == 1


def test_classification_report_is_generated_from_evaluation_values():
    evaluation = {
        "classifierHeadSha256": "head",
        "manifestSha256": "manifest",
        "sampleCount": 4,
        "correctCount": 3,
        "accuracy": 0.75,
        "macroPrecision": 0.625,
        "macroRecall": 0.75,
        "macroF1": 2 / 3,
        "classOrder": list(CLASS_LABELS),
        "perClass": {
            label: {"precision": 1.0, "recall": 1.0, "f1": 1.0, "support": 1}
            for label in CLASS_LABELS
        },
        "confusionMatrix": [[1, 0, 0, 0]] * 4,
    }

    report = classification_report_markdown(evaluation)

    assert "Accuracy: 0.7500000000" in report
    assert "Macro-F1: 0.6666666667" in report
    assert "| POTHOLE | 1.0000000000" in report
    assert "previously inspected during Phase 3C" in report


def test_confidence_analysis_separates_correct_incorrect_and_fixed_bins():
    predictions = build_sample_predictions(
        evaluation_manifest().entries,
        torch.tensor([
            [4.0, 0.0, 0.0, 0.0],
            [0.0, 2.0, 0.0, 0.0],
            [0.0, 0.0, 1.0, 0.0],
            [0.0, 0.0, 2.0, 1.0],
        ]),
    )

    analysis = analyze_prediction_confidence(predictions)

    assert analysis["overall"]["count"] == 4
    assert analysis["correct"]["count"] == 3
    assert analysis["incorrect"]["count"] == 1
    assert sum(item["count"] for item in analysis["bins"]) == 4
    assert "not calibrated" in analysis["interpretation"]


def test_misclassification_records_match_incorrect_predictions():
    predictions = build_sample_predictions(
        evaluation_manifest().entries,
        torch.tensor([
            [4.0, 0.0, 0.0, 0.0],
            [0.0, 4.0, 0.0, 0.0],
            [0.0, 0.0, 4.0, 0.0],
            [0.0, 0.0, 4.0, 1.0],
        ]),
    )

    records = build_misclassification_records(predictions)

    assert len(records) == 1
    assert records[0]["actualLabel"] == "POTHOLE"
    assert records[0]["predictedLabel"] == "DAMAGED_SIGN"
    assert "causal claim" in records[0]["interpretation"]
