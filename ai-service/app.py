from io import BytesIO
import warnings

from fastapi import FastAPI, File, HTTPException, UploadFile
from PIL import Image, UnidentifiedImageError

from schemas.prediction import PredictionResponse
from services.classifier import ConfidencePolicy, MunicipalIssueClassifier


app = FastAPI(title="Municipal Issue AI Service", version="1.0.0")
classifier = MunicipalIssueClassifier()
confidence_policy = ConfidencePolicy.from_environment()

MAX_IMAGE_BYTES = 10 * 1024 * 1024
Image.MAX_IMAGE_PIXELS = 40_000_000
CATEGORIES = {
    "POTHOLE": "ROAD",
    "ROAD_CRACK": "ROAD",
    "GARBAGE_ACCUMULATION": "SANITATION",
    "WATERLOGGING": "DRAINAGE",
    "DAMAGED_STREETLIGHT": "LIGHTING",
    "OPEN_MANHOLE": "SEWER",
    "OTHER": "OTHER",
}


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/predict", response_model=PredictionResponse)
def predict(image: UploadFile = File(...)) -> PredictionResponse:
    if image.content_type not in {"image/jpeg", "image/png"}:
        raise HTTPException(status_code=400, detail="Only JPEG and PNG images are supported")
    content = image.file.read(MAX_IMAGE_BYTES + 1)
    if not content:
        raise HTTPException(status_code=400, detail="Image is empty")
    if len(content) > MAX_IMAGE_BYTES:
        raise HTTPException(status_code=413, detail="Image exceeds 10 MB")
    try:
        with warnings.catch_warnings():
            warnings.simplefilter("error", Image.DecompressionBombWarning)
            decoded = Image.open(BytesIO(content))
            decoded.load()
    except (UnidentifiedImageError, OSError, Image.DecompressionBombError,
            Image.DecompressionBombWarning):
        raise HTTPException(status_code=400, detail="Image is corrupt or unsafe") from None

    prediction = classifier.classify(decoded)
    if prediction.issue_type not in CATEGORIES:
        raise HTTPException(status_code=502, detail="Model returned an unsupported issue type")
    confidence_level = confidence_policy.level(prediction.issue_score)
    return PredictionResponse(
        issueType=None if confidence_level == "LOW" else prediction.issue_type,
        candidateIssueType=prediction.issue_type,
        confidence=prediction.issue_score,
        confidenceLevel=confidence_level,
        category=CATEGORIES[prediction.issue_type],
        suggestedSeverity=prediction.severity,
        requiresManualReview=confidence_level == "LOW",
    )
