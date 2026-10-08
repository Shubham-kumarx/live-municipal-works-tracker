from io import BytesIO

from fastapi.testclient import TestClient
from PIL import Image
import pytest

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


def jpeg_bytes() -> bytes:
    output = BytesIO()
    Image.new("RGB", (8, 8), "gray").save(output, format="JPEG")
    return output.getvalue()


def test_health():
    assert TestClient(app_module.app).get("/health").json() == {"status": "ok"}


def test_application_lifespan_loads_model_once(monkeypatch):
    class LoadingClassifier:
        def __init__(self):
            self.load_count = 0

        def load(self):
            self.load_count += 1

    loading_classifier = LoadingClassifier()
    monkeypatch.setattr(app_module, "classifier", loading_classifier)

    with TestClient(app_module.app) as client:
        assert client.get("/health").json() == {"status": "ok"}
        assert client.get("/health").json() == {"status": "ok"}

    assert loading_classifier.load_count == 1


def test_predict_returns_typed_high_confidence_result(monkeypatch):
    monkeypatch.setattr(app_module, "classifier", FakeClassifier(0.8))
    monkeypatch.setattr(app_module, "confidence_policy", ConfidencePolicy(0.60, 0.80))
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
    monkeypatch.setattr(app_module, "confidence_policy", ConfidencePolicy(0.60, 0.80))
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
    monkeypatch.setattr(app_module, "classifier", FakeClassifier(0.60))
    monkeypatch.setattr(app_module, "confidence_policy", ConfidencePolicy(0.60, 0.80))
    response = TestClient(app_module.app).post(
        "/predict", files={"image": ("road.png", png_bytes(), "image/png")}
    )
    assert response.status_code == 200
    assert response.json() == {
        "issueType": "POTHOLE",
        "candidateIssueType": "POTHOLE",
        "confidence": 0.60,
        "confidenceLevel": "MEDIUM",
        "category": "ROAD",
        "suggestedSeverity": "HIGH",
        "requiresManualReview": False,
    }


@pytest.mark.parametrize(
    ("issue_type", "category"),
    [
        ("DOMESTIC_TRASH", "SANITATION"),
        ("ILLEGAL_PARKING", "ROAD"),
        ("DAMAGED_SIGN", "ROAD"),
        ("POTHOLE", "ROAD"),
    ],
)
def test_predict_supports_exact_four_class_vocabulary(monkeypatch, issue_type, category):
    class Classifier:
        def classify(self, _image):
            return RawPrediction(issue_type, 0.8, "HIGH", 0.7)

    monkeypatch.setattr(app_module, "classifier", Classifier())
    monkeypatch.setattr(app_module, "confidence_policy", ConfidencePolicy(0.60, 0.80))
    response = TestClient(app_module.app).post(
        "/predict", files={"image": ("issue.png", png_bytes(), "image/png")}
    )

    assert response.status_code == 200
    assert response.json()["candidateIssueType"] == issue_type
    assert response.json()["category"] == category
    assert set(response.json()) == {
        "issueType",
        "candidateIssueType",
        "confidence",
        "confidenceLevel",
        "category",
        "suggestedSeverity",
        "requiresManualReview",
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


@pytest.mark.parametrize(
    ("filename", "content", "content_type"),
    [
        ("road.jpg", png_bytes(), "image/png"),
        ("road.png", jpeg_bytes(), "image/jpeg"),
        ("road", png_bytes(), "image/png"),
    ],
)
def test_predict_rejects_extension_mismatch_before_inference(
        monkeypatch, filename, content, content_type):
    class UnexpectedClassifier:
        def classify(self, _image):
            raise AssertionError("classifier must not be called")

    monkeypatch.setattr(app_module, "classifier", UnexpectedClassifier())
    response = TestClient(app_module.app).post(
        "/predict", files={"image": (filename, content, content_type)}
    )

    assert response.status_code == 400
    assert response.json() == {
        "detail": "Image extension does not match its declared type",
    }


def test_predict_rejects_content_mismatch_before_inference(monkeypatch):
    class UnexpectedClassifier:
        def classify(self, _image):
            raise AssertionError("classifier must not be called")

    monkeypatch.setattr(app_module, "classifier", UnexpectedClassifier())
    response = TestClient(app_module.app).post(
        "/predict", files={"image": ("road.jpg", png_bytes(), "image/jpeg")}
    )

    assert response.status_code == 400
    assert response.json() == {
        "detail": "Image content does not match its declared type",
    }


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


@pytest.mark.parametrize(
    "unsupported_class",
    ["INFRASTRUCTURE_DAMAGE", "VANDALISM", "GRAFFITI", "VANDALISM_GRAFFITI"],
)
def test_predict_rejects_excluded_model_class_with_controlled_error(
        monkeypatch, unsupported_class):
    class UnsupportedClassifier:
        def classify(self, _image):
            return RawPrediction(unsupported_class, 0.9, "HIGH", 0.7)

    monkeypatch.setattr(app_module, "classifier", UnsupportedClassifier())
    response = TestClient(app_module.app).post(
        "/predict", files={"image": ("road.png", png_bytes(), "image/png")}
    )
    assert response.status_code == 502
    assert response.json() == {"detail": "Model returned an unsupported issue type"}
