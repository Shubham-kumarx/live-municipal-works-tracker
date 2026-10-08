from dataclasses import replace
from pathlib import Path

from PIL import Image
import pytest
import torch

from services.classifier import MODEL_ID, MODEL_REVISION
from training.model import (
    FourClassHead,
    classification_metrics,
    load_model_artifact,
)
from training.train import (
    TrainingConfig,
    assess_fine_tuning,
    build_evaluation_document,
    deterministic_training_view,
    train_classifier_head,
)


def test_training_configuration_has_reproducible_defaults():
    config = TrainingConfig()

    config.validate()
    assert config.seed == 2026
    assert config.image_size == 224
    assert config.feature_batch_size == 16
    assert config.batch_size == 64
    assert config.learning_rate == 0.001
    assert config.epochs == 50
    assert config.patience == 8


@pytest.mark.parametrize(
    "config",
    [
        replace(TrainingConfig(), batch_size=0),
        replace(TrainingConfig(), learning_rate=0.0),
        replace(TrainingConfig(), weight_decay=-1.0),
    ],
)
def test_training_configuration_rejects_invalid_values(config):
    with pytest.raises(ValueError):
        config.validate()


def test_training_augmentation_is_deterministic_for_seed():
    image = Image.new("RGB", (320, 240), (20, 80, 140))

    first = deterministic_training_view(image, 224, 17)
    second = deterministic_training_view(image, 224, 17)

    assert first.size == (224, 224)
    assert first.tobytes() == second.tobytes()


def test_classifier_head_uses_fixed_embedding_and_class_dimensions():
    logits = FourClassHead()(torch.zeros((3, 512)))

    assert logits.shape == (3, 4)


def test_classification_metrics_use_canonical_four_class_shape():
    metrics = classification_metrics(
        torch.tensor([0, 1, 2, 0]),
        torch.tensor([0, 1, 3, 3]),
    )

    assert metrics.accuracy == 0.5
    assert metrics.confusion_matrix == [
        [1, 0, 0, 0],
        [0, 1, 0, 0],
        [0, 0, 0, 0],
        [1, 0, 1, 0],
    ]


def test_linear_probe_training_is_deterministic():
    generator = torch.Generator().manual_seed(12)
    training_embeddings = torch.randn((40, 512), generator=generator)
    training_targets = torch.tensor([index % 4 for index in range(40)])
    validation_embeddings = torch.randn((20, 512), generator=generator)
    validation_targets = torch.tensor([index % 4 for index in range(20)])
    config = replace(TrainingConfig(), epochs=4, patience=2, batch_size=8)

    first = train_classifier_head(
        training_embeddings,
        training_targets,
        validation_embeddings,
        validation_targets,
        config,
    )
    second = train_classifier_head(
        training_embeddings,
        training_targets,
        validation_embeddings,
        validation_targets,
        config,
    )

    assert first.history == second.history
    assert first.best_epoch == second.best_epoch
    for name, tensor in first.head.state_dict().items():
        assert torch.equal(tensor, second.head.state_dict()[name])


def test_effective_linear_probe_does_not_trigger_backbone_fine_tuning():
    generator = torch.Generator().manual_seed(4)
    embeddings = torch.randn((40, 512), generator=generator)
    targets = torch.tensor([index % 4 for index in range(40)])
    result = train_classifier_head(
        embeddings,
        targets,
        embeddings,
        targets,
        replace(TrainingConfig(), epochs=40, patience=8, batch_size=8),
    )

    decision = assess_fine_tuning(result)

    assert decision["performed"] is False
    assert decision["decision"] == "SKIPPED_LINEAR_PROBE_ALREADY_EFFECTIVE"


def test_generated_best_checkpoint_reloads_with_fixed_class_order():
    head, metadata = load_model_artifact(
        Path("models/four-class-clip"),
        expected_model_id=MODEL_ID,
        expected_model_revision=MODEL_REVISION,
    )

    assert metadata["selectionMetric"] == "validationMacroF1"
    assert metadata["selectionTieBreaker"] == "validationLoss"
    assert metadata["bestEpoch"] == 50
    assert head(torch.zeros((2, 512))).shape == (2, 4)


def test_evaluation_document_preserves_measured_counts_and_class_order():
    metrics = classification_metrics(
        torch.tensor([0, 1, 2, 3]),
        torch.tensor([0, 1, 2, 3]),
    )

    document = build_evaluation_document(
        0.25, metrics, 4, "manifest", "checkpoint"
    )

    assert document["sampleCount"] == 4
    assert document["accuracy"] == 1.0
    assert document["macroF1"] == 1.0
    assert document["classOrder"] == [
        "DOMESTIC_TRASH", "ILLEGAL_PARKING", "DAMAGED_SIGN", "POTHOLE"
    ]
