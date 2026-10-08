from __future__ import annotations

import argparse
import hashlib
import json
import random
import time
from dataclasses import asdict, dataclass
from pathlib import Path

import numpy as np
from PIL import Image, ImageEnhance
import torch
from torch import nn
from torch.nn import functional as functional
from torch.utils.data import DataLoader, TensorDataset
from transformers import CLIPModel, CLIPProcessor

from issue_types import CLASS_LABELS, CLASS_TO_INDEX
from services.classifier import MODEL_CACHE, MODEL_ID, MODEL_REVISION
from training.dataset import ManifestEntry, decode_image, load_training_manifest
from training.evaluation import (
    analyze_prediction_confidence,
    build_misclassification_records,
    build_sample_predictions,
    calculate_test_metrics,
    write_classification_report,
    write_confusion_matrix,
    write_json_atomic,
    load_best_validation_checkpoint,
    validate_class_index_mapping,
    write_test_predictions,
)
from training.model import (
    ClassificationMetrics,
    FourClassHead,
    classification_metrics,
    save_head_weights,
)


@dataclass(frozen=True)
class TrainingConfig:
    seed: int = 2026
    image_size: int = 224
    feature_batch_size: int = 16
    batch_size: int = 64
    epochs: int = 50
    learning_rate: float = 0.001
    weight_decay: float = 0.01
    patience: int = 8
    minimum_improvement: float = 1e-6

    def validate(self) -> None:
        integer_values = {
            "seed": self.seed,
            "image_size": self.image_size,
            "feature_batch_size": self.feature_batch_size,
            "batch_size": self.batch_size,
            "epochs": self.epochs,
            "patience": self.patience,
        }
        for name, value in integer_values.items():
            if not isinstance(value, int) or value <= 0:
                raise ValueError(f"{name} must be a positive integer")
        if self.image_size != 224:
            raise ValueError("image_size must remain 224 for the pinned CLIP backbone")
        if not 0.0 < self.learning_rate <= 1.0:
            raise ValueError("learning_rate must be in (0, 1]")
        if not 0.0 <= self.weight_decay <= 1.0:
            raise ValueError("weight_decay must be in [0, 1]")
        if self.minimum_improvement < 0.0:
            raise ValueError("minimum_improvement must be non-negative")


def configure_reproducibility(seed: int) -> None:
    random.seed(seed)
    np.random.seed(seed)
    torch.manual_seed(seed)
    torch.use_deterministic_algorithms(True)


def deterministic_training_view(
        image: Image.Image,
        image_size: int,
        seed: int) -> Image.Image:
    rng = random.Random(seed)
    image = image.convert("RGB")
    width, height = image.size
    crop_fraction = rng.uniform(0.90, 1.0)
    crop_width = max(1, round(width * crop_fraction))
    crop_height = max(1, round(height * crop_fraction))
    left = rng.randint(0, max(0, width - crop_width))
    top = rng.randint(0, max(0, height - crop_height))
    image = image.crop((left, top, left + crop_width, top + crop_height))
    image = image.resize((image_size, image_size), Image.Resampling.BICUBIC)
    image = image.rotate(
        rng.uniform(-5.0, 5.0),
        resample=Image.Resampling.BICUBIC,
        fillcolor=(128, 128, 128),
    )
    image = ImageEnhance.Brightness(image).enhance(rng.uniform(0.90, 1.10))
    return ImageEnhance.Contrast(image).enhance(rng.uniform(0.90, 1.10))


@dataclass(frozen=True)
class HeadTrainingResult:
    head: FourClassHead
    best_epoch: int
    history: list[dict[str, float | int]]
    best_validation: ClassificationMetrics
    stopped_early: bool


def _json_write_atomic(path: Path, value: dict | list) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary_path = path.with_suffix(path.suffix + ".tmp")
    temporary_path.write_text(
        json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )
    temporary_path.replace(path)


def _augmentation_seed(base_seed: int, entry: ManifestEntry) -> int:
    digest = hashlib.sha256(
        f"{base_seed}:{entry.relative_path}".encode("utf-8")
    ).digest()
    return int.from_bytes(digest[:8], byteorder="big")


def _load_clip_backbone(device: str):
    options = {
        "cache_dir": MODEL_CACHE,
        "revision": MODEL_REVISION,
    }
    try:
        processor = CLIPProcessor.from_pretrained(
            MODEL_ID, use_fast=False, local_files_only=True, **options
        )
        model = CLIPModel.from_pretrained(
            MODEL_ID, local_files_only=True, **options
        )
    except OSError:
        processor = CLIPProcessor.from_pretrained(
            MODEL_ID, use_fast=False, local_files_only=False, **options
        )
        model = CLIPModel.from_pretrained(
            MODEL_ID, local_files_only=False, **options
        )
    model = model.to(device)
    model.eval()
    for parameter in model.parameters():
        parameter.requires_grad = False
    return processor, model


def extract_embeddings(
        entries: tuple[ManifestEntry, ...],
        processor,
        backbone,
        device: str,
        config: TrainingConfig,
        *,
        augmented: bool = False) -> tuple[torch.Tensor, torch.Tensor]:
    embeddings: list[torch.Tensor] = []
    targets: list[int] = []
    total_batches = (len(entries) + config.feature_batch_size - 1) // config.feature_batch_size
    for batch_number, start in enumerate(
            range(0, len(entries), config.feature_batch_size), start=1):
        batch_entries = entries[start:start + config.feature_batch_size]
        images = []
        for entry in batch_entries:
            image = decode_image(entry)
            if augmented:
                image = deterministic_training_view(
                    image,
                    config.image_size,
                    _augmentation_seed(config.seed, entry),
                )
            images.append(image)
            targets.append(CLASS_TO_INDEX[entry.label])
        inputs = processor(images=images, return_tensors="pt")
        pixel_values = inputs["pixel_values"].to(device)
        with torch.inference_mode():
            features = backbone.get_image_features(pixel_values=pixel_values)
            if not isinstance(features, torch.Tensor):
                features = features.pooler_output
            features = functional.normalize(features, dim=1)
        embeddings.append(features.to(device="cpu"))
        if batch_number == 1 or batch_number == total_batches or batch_number % 10 == 0:
            view = "augmented" if augmented else "standard"
            print(
                f"Extracting {view} embeddings: batch {batch_number}/{total_batches}",
                flush=True,
            )
    return torch.cat(embeddings), torch.tensor(targets, dtype=torch.long)


def _class_weights(targets: torch.Tensor) -> torch.Tensor:
    counts = torch.bincount(targets, minlength=len(CLASS_LABELS)).to(torch.float32)
    if (counts == 0).any():
        raise ValueError("Every class must appear in the training split")
    return targets.numel() / (len(CLASS_LABELS) * counts)


def _evaluate_head(
        head: FourClassHead,
        embeddings: torch.Tensor,
        targets: torch.Tensor,
        criterion: nn.Module) -> tuple[float, ClassificationMetrics]:
    head.eval()
    with torch.inference_mode():
        logits = head(embeddings)
        loss = float(criterion(logits, targets).item())
        metrics = classification_metrics(logits.argmax(dim=1), targets)
    return loss, metrics


def train_classifier_head(
        training_embeddings: torch.Tensor,
        training_targets: torch.Tensor,
        validation_embeddings: torch.Tensor,
        validation_targets: torch.Tensor,
        config: TrainingConfig) -> HeadTrainingResult:
    config.validate()
    configure_reproducibility(config.seed)
    head = FourClassHead()
    criterion = nn.CrossEntropyLoss(weight=_class_weights(training_targets))
    optimizer = torch.optim.AdamW(
        head.parameters(), lr=config.learning_rate, weight_decay=config.weight_decay
    )
    generator = torch.Generator().manual_seed(config.seed)
    loader = DataLoader(
        TensorDataset(training_embeddings, training_targets),
        batch_size=config.batch_size,
        shuffle=True,
        generator=generator,
        num_workers=0,
    )

    history: list[dict[str, float | int]] = []
    best_state: dict[str, torch.Tensor] | None = None
    best_epoch = 0
    best_f1 = -1.0
    best_loss = float("inf")
    best_validation: ClassificationMetrics | None = None
    epochs_without_improvement = 0
    stopped_early = False

    for epoch in range(1, config.epochs + 1):
        head.train()
        for embeddings, targets in loader:
            optimizer.zero_grad(set_to_none=True)
            loss = criterion(head(embeddings), targets)
            loss.backward()
            optimizer.step()

        training_loss, training_metrics = _evaluate_head(
            head, training_embeddings, training_targets, criterion
        )
        validation_loss, validation_metrics = _evaluate_head(
            head, validation_embeddings, validation_targets, criterion
        )
        history.append({
            "epoch": epoch,
            "learningRate": optimizer.param_groups[0]["lr"],
            "trainingLoss": training_loss,
            "validationLoss": validation_loss,
            "trainingAccuracy": training_metrics.accuracy,
            "validationAccuracy": validation_metrics.accuracy,
            "trainingMacroF1": training_metrics.macro_f1,
            "validationMacroF1": validation_metrics.macro_f1,
        })
        print(
            f"Epoch {epoch:02d}: train_loss={training_loss:.6f} "
            f"val_loss={validation_loss:.6f} "
            f"train_acc={training_metrics.accuracy:.4f} "
            f"val_acc={validation_metrics.accuracy:.4f} "
            f"val_macro_f1={validation_metrics.macro_f1:.4f}",
            flush=True,
        )

        f1_improved = validation_metrics.macro_f1 > (
            best_f1 + config.minimum_improvement
        )
        tied_f1 = abs(validation_metrics.macro_f1 - best_f1) <= config.minimum_improvement
        loss_improved = validation_loss < best_loss
        if f1_improved or (tied_f1 and loss_improved):
            best_state = {
                name: tensor.detach().clone()
                for name, tensor in head.state_dict().items()
            }
            best_epoch = epoch
            best_f1 = validation_metrics.macro_f1
            best_loss = validation_loss
            best_validation = validation_metrics
            epochs_without_improvement = 0
        else:
            epochs_without_improvement += 1
            if epochs_without_improvement >= config.patience:
                stopped_early = True
                break

    if best_state is None or best_validation is None:
        raise RuntimeError("Training did not produce a valid checkpoint")
    head.load_state_dict(best_state)
    head.eval()
    return HeadTrainingResult(
        head=head,
        best_epoch=best_epoch,
        history=history,
        best_validation=best_validation,
        stopped_early=stopped_early,
    )


def assess_fine_tuning(result: HeadTrainingResult) -> dict[str, bool | float | str]:
    best_history = next(
        row for row in result.history if row["epoch"] == result.best_epoch
    )
    generalization_gap = (
        float(best_history["trainingMacroF1"])
        - float(best_history["validationMacroF1"])
    )
    if result.best_validation.macro_f1 >= 0.98 and generalization_gap <= 0.03:
        return {
            "performed": False,
            "decision": "SKIPPED_LINEAR_PROBE_ALREADY_EFFECTIVE",
            "validationMacroF1": result.best_validation.macro_f1,
            "trainingValidationMacroF1Gap": generalization_gap,
            "reason": (
                "The frozen-backbone head achieved at least 0.98 validation macro-F1 "
                "with no material train/validation gap; unfreezing CLIP would add "
                "overfitting risk for little measurable validation headroom."
            ),
        }
    return {
        "performed": False,
        "decision": "NOT_RUN_REQUIRES_SEPARATE_EVIDENCE",
        "validationMacroF1": result.best_validation.macro_f1,
        "trainingValidationMacroF1Gap": generalization_gap,
        "reason": (
            "The linear-probe result does not by itself justify unfreezing the backbone; "
            "review class-level errors and acquire approval for a bounded comparison run."
        ),
    }


def run_head_training(args: argparse.Namespace, config: TrainingConfig, manifest) -> dict:
    if args.output_dir is None:
        raise ValueError("--output-dir is required for training")
    output_dir = args.output_dir.resolve()
    repository_root = args.repository_root.resolve()
    if not output_dir.is_relative_to(repository_root):
        raise ValueError("Training output directory must remain inside the repository")

    started = time.perf_counter()
    device = "cuda" if torch.cuda.is_available() else "cpu"
    processor, backbone = _load_clip_backbone(device)
    train_entries = manifest.for_split("train")
    validation_entries = manifest.for_split("validation")
    standard_train, train_targets = extract_embeddings(
        train_entries, processor, backbone, device, config
    )
    augmented_train, augmented_targets = extract_embeddings(
        train_entries, processor, backbone, device, config, augmented=True
    )
    training_embeddings = torch.cat((standard_train, augmented_train))
    training_targets = torch.cat((train_targets, augmented_targets))
    validation_embeddings, validation_targets = extract_embeddings(
        validation_entries, processor, backbone, device, config
    )
    del backbone

    result = train_classifier_head(
        training_embeddings,
        training_targets,
        validation_embeddings,
        validation_targets,
        config,
    )
    head_path = output_dir / "classifier_head.pt"
    head_digest = save_head_weights(result.head, head_path)
    history_document = {
        "schemaVersion": 1,
        "bestEpoch": result.best_epoch,
        "stoppedEarly": result.stopped_early,
        "epochsCompleted": len(result.history),
        "history": result.history,
    }
    _json_write_atomic(output_dir / "training_history.json", history_document)
    metadata = {
        "schemaVersion": 1,
        "architecture": "CLIP_VIT_B32_FROZEN_LINEAR_PROBE",
        "baseModelId": MODEL_ID,
        "baseModelRevision": MODEL_REVISION,
        "embeddingSize": 512,
        "classLabels": list(CLASS_LABELS),
        "classToIndex": CLASS_TO_INDEX,
        "inputImageSize": config.image_size,
        "manifestSha256": manifest.manifest_sha256,
        "inventorySha256": manifest.report["inventorySha256"],
        "assignmentDigest": manifest.report["assignmentDigest"],
        "trainingConfiguration": asdict(config),
        "optimizer": "AdamW",
        "loss": "inverse-frequency class-weighted cross entropy",
        "augmentation": {
            "viewsPerTrainingImage": 2,
            "randomCropFraction": [0.90, 1.0],
            "rotationDegrees": [-5.0, 5.0],
            "brightnessFactor": [0.90, 1.10],
            "contrastFactor": [0.90, 1.10],
            "horizontalFlip": False,
        },
        "selectionMetric": "validationMacroF1",
        "selectionTieBreaker": "validationLoss",
        "bestEpoch": result.best_epoch,
        "bestValidationMacroF1": result.best_validation.macro_f1,
        "classifierHeadSha256": head_digest,
        "fineTuning": assess_fine_tuning(result),
        "trainingDevice": device,
        "trainingDurationSeconds": time.perf_counter() - started,
    }
    _json_write_atomic(output_dir / "model_metadata.json", metadata)
    return metadata


def build_evaluation_document(
        loss: float,
        metrics: ClassificationMetrics,
        sample_count: int,
        manifest_sha256: str,
        classifier_head_sha256: str) -> dict:
    if sum(sum(row) for row in metrics.confusion_matrix) != sample_count:
        raise ValueError("Confusion-matrix count does not match the evaluation split")
    return {
        "schemaVersion": 1,
        "split": "test",
        "sampleCount": sample_count,
        "correctCount": sum(
            metrics.confusion_matrix[index][index]
            for index in range(len(CLASS_LABELS))
        ),
        "loss": loss,
        "accuracy": metrics.accuracy,
        "macroPrecision": metrics.macro_precision,
        "macroRecall": metrics.macro_recall,
        "macroF1": metrics.macro_f1,
        "perClass": metrics.per_class,
        "confusionMatrix": metrics.confusion_matrix,
        "classOrder": list(CLASS_LABELS),
        "manifestSha256": manifest_sha256,
        "classifierHeadSha256": classifier_head_sha256,
    }


def evaluate_saved_head(
        args: argparse.Namespace,
        config: TrainingConfig,
        manifest) -> dict:
    if args.output_dir is None:
        raise ValueError("--output-dir is required for evaluation")
    output_dir = args.output_dir.resolve()
    repository_root = args.repository_root.resolve()
    if not output_dir.is_relative_to(repository_root):
        raise ValueError("Evaluation output directory must remain inside the repository")
    checkpoint = load_best_validation_checkpoint(output_dir)
    validate_class_index_mapping(checkpoint, manifest)
    head = checkpoint.head
    metadata = checkpoint.metadata
    device = "cuda" if torch.cuda.is_available() else "cpu"
    processor, backbone = _load_clip_backbone(device)
    test_entries = manifest.for_split("test")
    test_embeddings, test_targets = extract_embeddings(
        test_entries, processor, backbone, device, config
    )
    del backbone

    head.eval()
    with torch.inference_mode():
        test_logits = head(test_embeddings)
    predictions = build_sample_predictions(test_entries, test_logits)
    write_test_predictions(predictions, output_dir / "test_predictions.csv")

    training_targets = torch.tensor(
        [CLASS_TO_INDEX[entry.label] for entry in manifest.for_split("train")],
        dtype=torch.long,
    )
    criterion = nn.CrossEntropyLoss(weight=_class_weights(training_targets))
    test_loss, test_metrics = _evaluate_head(
        head, test_embeddings, test_targets, criterion
    )
    prediction_metrics = calculate_test_metrics(predictions)
    if prediction_metrics != test_metrics:
        raise RuntimeError("Saved predictions do not reproduce calculated test metrics")
    write_confusion_matrix(
        test_metrics, output_dir / "confusion_matrix.csv"
    )
    evaluation = build_evaluation_document(
        test_loss,
        test_metrics,
        len(test_entries),
        manifest.manifest_sha256,
        metadata["classifierHeadSha256"],
    )
    confidence_analysis = analyze_prediction_confidence(predictions)
    evaluation["confidenceAnalysis"] = confidence_analysis
    misclassifications = build_misclassification_records(predictions)
    off_diagonal_count = evaluation["sampleCount"] - evaluation["correctCount"]
    if len(misclassifications) != off_diagonal_count:
        raise RuntimeError(
            "Misclassification records do not match the confusion matrix"
        )
    evaluation["misclassificationCount"] = len(misclassifications)
    _json_write_atomic(output_dir / "evaluation.json", evaluation)
    write_json_atomic(
        confidence_analysis, output_dir / "confidence_analysis.json"
    )
    write_json_atomic(
        misclassifications, output_dir / "misclassifications.json"
    )
    write_classification_report(
        evaluation, output_dir / "classification_report.md"
    )
    return evaluation


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Train the four-class civic-issue classifier"
    )
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--repository-root", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path)
    parser.add_argument("--seed", type=int, default=2026)
    parser.add_argument("--image-size", type=int, default=224)
    parser.add_argument("--feature-batch-size", type=int, default=16)
    parser.add_argument("--batch-size", type=int, default=64)
    parser.add_argument("--epochs", type=int, default=50)
    parser.add_argument("--learning-rate", type=float, default=0.001)
    parser.add_argument("--weight-decay", type=float, default=0.01)
    parser.add_argument("--patience", type=int, default=8)
    parser.add_argument("--validate-data-only", action="store_true")
    parser.add_argument("--evaluate-only", action="store_true")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    config = TrainingConfig(
        seed=args.seed,
        image_size=args.image_size,
        feature_batch_size=args.feature_batch_size,
        batch_size=args.batch_size,
        epochs=args.epochs,
        learning_rate=args.learning_rate,
        weight_decay=args.weight_decay,
        patience=args.patience,
    )
    config.validate()
    configure_reproducibility(config.seed)
    manifest = load_training_manifest(
        args.manifest, args.report, args.repository_root
    )
    if args.validate_data_only:
        print(json.dumps({
            "configuration": asdict(config),
            "manifestSha256": manifest.manifest_sha256,
            "splitCounts": {
                split: len(manifest.for_split(split))
                for split in ("train", "validation", "test")
            },
            "status": "READY_FOR_TRAINING",
        }, indent=2, sort_keys=True))
        return
    if args.evaluate_only:
        evaluation = evaluate_saved_head(args, config, manifest)
        print(json.dumps(evaluation, indent=2, sort_keys=True))
        return
    metadata = run_head_training(args, config, manifest)
    print(json.dumps(metadata, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
