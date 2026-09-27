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
    assert response.json()["issueType"] is None
    assert response.json()["candidateIssueType"] == "POTHOLE"
    assert response.json()["requiresManualReview"] is True


def test_predict_rejects_corrupt_image():
    response = TestClient(app_module.app).post(
        "/predict", files={"image": ("road.png", b"not-an-image", "image/png")}
    )
    assert response.status_code == 400
