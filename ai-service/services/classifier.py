from dataclasses import dataclass
import math
import os
from pathlib import Path

MODEL_ID = os.getenv("AI_MODEL_ID", "openai/clip-vit-base-patch32")
MODEL_CACHE = Path(__file__).resolve().parents[1] / ".model-cache"
os.environ.setdefault("HF_HOME", str(MODEL_CACHE))
os.environ.setdefault("HF_HUB_CACHE", str(MODEL_CACHE / "hub"))

from PIL import Image
import torch
from transformers import CLIPModel, CLIPProcessor

ISSUE_PROMPTS = {
    "POTHOLE": "a photo of a pothole in a road",
    "ROAD_CRACK": "a photo of a cracked or broken road surface",
    "GARBAGE_ACCUMULATION": "a photo of accumulated garbage or an overflowing waste pile",
    "WATERLOGGING": "a photo of waterlogging or flooding on a street",
    "DAMAGED_STREETLIGHT": "a photo of a damaged or non-working streetlight",
    "OPEN_MANHOLE": "a photo of an open or dangerously uncovered manhole",
    "OTHER": "a photo with no visible supported municipal infrastructure issue",
}

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
        medium = float(os.getenv("AI_MEDIUM_CONFIDENCE_THRESHOLD", "0.45"))
        high = float(os.getenv("AI_HIGH_CONFIDENCE_THRESHOLD", "0.70"))
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
        self._device = "cuda" if torch.cuda.is_available() else "cpu"

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

    @staticmethod
    def _load_components(local_files_only: bool):
        options = {"cache_dir": MODEL_CACHE, "local_files_only": local_files_only}
        processor = CLIPProcessor.from_pretrained(MODEL_ID, **options)
        model = CLIPModel.from_pretrained(MODEL_ID, **options)
        return processor, model

    def classify(self, image: Image.Image) -> RawPrediction:
        self._load()
        image = image.convert("RGB")
        issue_type, issue_score = self._rank(image, ISSUE_PROMPTS)
        severity, severity_score = self._rank(image, SEVERITY_PROMPTS)
        return RawPrediction(issue_type, issue_score, severity, severity_score)

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
