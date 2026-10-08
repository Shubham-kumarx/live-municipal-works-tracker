from dataclasses import dataclass
import math
import os
from pathlib import Path

MODEL_ID = os.getenv("AI_MODEL_ID", "openai/clip-vit-base-patch32")
MODEL_REVISION = os.getenv(
    "AI_MODEL_REVISION", "3d74acf9a28c67741b2f4f2ea7635f0aaf6f0268"
)
MODEL_CACHE = Path(__file__).resolve().parents[1] / ".model-cache"
TRAINED_MODEL_DIR = Path(os.getenv(
    "AI_TRAINED_MODEL_DIR",
    str(Path(__file__).resolve().parents[1] / "models" / "four-class-clip"),
))
os.environ.setdefault("HF_HOME", str(MODEL_CACHE))
os.environ.setdefault("HF_HUB_CACHE", str(MODEL_CACHE / "hub"))

from PIL import Image
import torch
from torch.nn import functional as functional
from transformers import CLIPModel, CLIPProcessor

from issue_types import INDEX_TO_CLASS
from training.model import load_model_artifact

SEVERITY_PROMPTS = {
    "LOW": "a minor municipal issue with little immediate danger",
    "MEDIUM": "a moderate municipal issue needing routine attention",
    "HIGH": "a severe municipal issue creating a significant public hazard",
    "CRITICAL": "a critical municipal issue creating an immediate danger to life or safety",
}


@dataclass(frozen=True)
class RawPrediction:
    issue_type: str
    issue_score: float
    severity: str
    severity_score: float


@dataclass(frozen=True)
class ConfidencePolicy:
    medium_threshold: float
    high_threshold: float

    @classmethod
    def from_environment(cls) -> "ConfidencePolicy":
        medium = float(os.getenv("AI_MEDIUM_CONFIDENCE_THRESHOLD", "0.60"))
        high = float(os.getenv("AI_HIGH_CONFIDENCE_THRESHOLD", "0.80"))
        if not 0.0 <= medium <= high <= 1.0:
            raise ValueError("Confidence thresholds must satisfy 0 <= medium <= high <= 1")
        return cls(medium, high)

    def level(self, score: float) -> str:
        if score >= self.high_threshold:
            return "HIGH"
        if score >= self.medium_threshold:
            return "MEDIUM"
        return "LOW"


class MunicipalIssueClassifier:
    def __init__(self) -> None:
        self._model = None
        self._processor = None
        self._issue_head = None
        self._model_metadata = None
        self._device = "cuda" if torch.cuda.is_available() else "cpu"

    def load(self) -> None:
        """Load and validate the shared inference components once."""
        self._load()

    def _load(self) -> None:
        if self._model is not None:
            return
        MODEL_CACHE.mkdir(parents=True, exist_ok=True)
        try:
            processor, model = self._load_components(local_files_only=True)
        except OSError:
            processor, model = self._load_components(local_files_only=False)
        self._processor = processor
        self._model = model.to(self._device)
        self._model.eval()
        self._issue_head, self._model_metadata = load_model_artifact(
            TRAINED_MODEL_DIR,
            expected_model_id=MODEL_ID,
            expected_model_revision=MODEL_REVISION,
            device=self._device,
        )

    @staticmethod
    def _load_components(local_files_only: bool):
        options = {
            "cache_dir": MODEL_CACHE,
            "local_files_only": local_files_only,
            "revision": MODEL_REVISION,
        }
        processor = CLIPProcessor.from_pretrained(MODEL_ID, use_fast=False, **options)
        model = CLIPModel.from_pretrained(MODEL_ID, **options)
        return processor, model

    def classify(self, image: Image.Image) -> RawPrediction:
        self._load()
        image = image.convert("RGB")
        issue_type, issue_score = self._classify_issue(image)
        severity, severity_score = self._rank(image, SEVERITY_PROMPTS)
        return RawPrediction(issue_type, issue_score, severity, severity_score)

    def _classify_issue(self, image: Image.Image) -> tuple[str, float]:
        inputs = self._processor(images=image, return_tensors="pt")
        pixel_values = inputs["pixel_values"].to(self._device)
        with torch.inference_mode():
            features = self._model.get_image_features(pixel_values=pixel_values)
            if not isinstance(features, torch.Tensor):
                features = features.pooler_output
            features = functional.normalize(features, dim=1)
            probabilities = self._issue_head(features).softmax(dim=1)[0]
        index = int(probabilities.argmax().item())
        score = float(probabilities[index].item())
        if index not in INDEX_TO_CLASS or not math.isfinite(score):
            raise RuntimeError("Trained classifier produced an invalid result")
        return INDEX_TO_CLASS[index], score

    def _rank(self, image: Image.Image, prompts: dict[str, str]) -> tuple[str, float]:
        labels = list(prompts)
        inputs = self._processor(
            text=[prompts[label] for label in labels],
            images=image,
            return_tensors="pt",
            padding=True,
        )
        inputs = {name: value.to(self._device) for name, value in inputs.items()}
        with torch.inference_mode():
            logits = self._model(**inputs).logits_per_image
            probabilities = logits.softmax(dim=1)[0]
        index = int(probabilities.argmax().item())
        score = float(probabilities[index].item())
        if not math.isfinite(score):
            raise RuntimeError("Model produced a non-finite score")
        return labels[index], score
