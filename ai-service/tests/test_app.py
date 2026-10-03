from io import BytesIO

from fastapi.testclient import TestClient
from PIL import Image

import app as app_module
from services.classifier import ConfidencePolicy, RawPrediction


class FakeClassifier:
    def __init__(self, score: float = 0.8):
        self.score = score

    def classify(self, _image):
        return RawPrediction("POTHOLE", self.score, "HIGH", 0.7)


def png_bytes() -> bytes:
    output = BytesIO()
    Image.new("RGB", (8, 8), "gray").save(output, format="PNG")
    return output.getvalue()


def test_health():
    assert TestClient(app_module.app).get("/health").json() == {"status": "ok"}


def test_predict_returns_typed_high_confidence_result(monkeypatch):
    monkeypatch.setattr(app_module, "classifier", FakeClassifier(0.8))
    monkeypatch.setattr(app_module, "confidence_policy", ConfidencePolicy(0.45, 0.7))
    response = TestClient(app_module.app).post(
        "/predict", files={"image": ("road.png", png_bytes(), "image/png")}
    )
    assert response.status_code == 200
    assert response.json() == {
        "issueType": "POTHOLE",
        "candidateIssueType": "POTHOLE",
        "confidence": 0.8,
        "confidenceLevel": "HIGH",
        "category": "ROAD",
        "suggestedSeverity": "HIGH",
        "requiresManualReview": False,
    }


def test_low_confidence_does_not_force_issue_type(monkeypatch):
    monkeypatch.setattr(app_module, "classifier", FakeClassifier(0.2))
    monkeypatch.setattr(app_module, "confidence_policy", ConfidencePolicy(0.45, 0.7))
    response = TestClient(app_module.app).post(
        "/predict", files={"image": ("road.png", png_bytes(), "image/png")}
    )
    assert response.status_code == 200
    assert response.json() == {
        "issueType": None,
        "candidateIssueType": "POTHOLE",
        "confidence": 0.2,
        "confidenceLevel": "LOW",
        "category": "ROAD",
        "suggestedSeverity": "HIGH",
        "requiresManualReview": True,
    }


def test_predict_returns_typed_medium_confidence_result(monkeypatch):
    monkeypatch.setattr(app_module, "classifier", FakeClassifier(0.45))
    monkeypatch.setattr(app_module, "confidence_policy", ConfidencePolicy(0.45, 0.7))
    response = TestClient(app_module.app).post(
        "/predict", files={"image": ("road.png", png_bytes(), "image/png")}
    )
    assert response.status_code == 200
    assert response.json() == {
        "issueType": "POTHOLE",
        "candidateIssueType": "POTHOLE",
        "confidence": 0.45,
        "confidenceLevel": "MEDIUM",
        "category": "ROAD",
        "suggestedSeverity": "HIGH",
        "requiresManualReview": False,
    }


def test_predict_rejects_corrupt_image(monkeypatch):
    class UnexpectedClassifier:
        def classify(self, _image):
            raise AssertionError("classifier must not be called")

    monkeypatch.setattr(app_module, "classifier", UnexpectedClassifier())
    response = TestClient(app_module.app).post(
        "/predict", files={"image": ("road.png", b"not-an-image", "image/png")}
    )
    assert response.status_code == 400
    assert response.json() == {"detail": "Image is corrupt or unsafe"}


def test_predict_rejects_empty_image_before_inference(monkeypatch):
    class UnexpectedClassifier:
        def classify(self, _image):
            raise AssertionError("classifier must not be called")

    monkeypatch.setattr(app_module, "classifier", UnexpectedClassifier())
    response = TestClient(app_module.app).post(
        "/predict", files={"image": ("road.png", b"", "image/png")}
    )
    assert response.status_code == 400
    assert response.json() == {"detail": "Image is empty"}


def test_predict_rejects_unsupported_format_before_inference(monkeypatch):
    class UnexpectedClassifier:
        def classify(self, _image):
            raise AssertionError("classifier must not be called")

    monkeypatch.setattr(app_module, "classifier", UnexpectedClassifier())
    response = TestClient(app_module.app).post(
        "/predict", files={"image": ("road.gif", b"GIF89a", "image/gif")}
    )
    assert response.status_code == 400
    assert response.json() == {"detail": "Only JPEG and PNG images are supported"}


def test_predict_rejects_oversized_image_before_inference(monkeypatch):
    class UnexpectedClassifier:
        def classify(self, _image):
            raise AssertionError("classifier must not be called")

    monkeypatch.setattr(app_module, "classifier", UnexpectedClassifier())
    response = TestClient(app_module.app).post(
        "/predict",
        files={"image": ("road.png", b"x" * (app_module.MAX_IMAGE_BYTES + 1), "image/png")},
    )
    assert response.status_code == 413
    assert response.json() == {"detail": "Image exceeds 10 MB"}


def test_predict_rejects_unsupported_model_class_with_controlled_error(monkeypatch):
    class UnsupportedClassifier:
        def classify(self, _image):
            return RawPrediction("UNSUPPORTED_CLASS", 0.9, "HIGH", 0.7)

    monkeypatch.setattr(app_module, "classifier", UnsupportedClassifier())
    response = TestClient(app_module.app).post(
        "/predict", files={"image": ("road.png", png_bytes(), "image/png")}
    )
    assert response.status_code == 502
    assert response.json() == {"detail": "Model returned an unsupported issue type"}
