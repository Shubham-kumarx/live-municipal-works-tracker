from contextlib import asynccontextmanager
from io import BytesIO
from pathlib import Path
import warnings

from fastapi import FastAPI, File, HTTPException, UploadFile
from PIL import Image, UnidentifiedImageError

from issue_types import ISSUE_CATEGORIES
from schemas.prediction import PredictionResponse
from services.classifier import ConfidencePolicy, MunicipalIssueClassifier


classifier = MunicipalIssueClassifier()
confidence_policy = ConfidencePolicy.from_environment()


@asynccontextmanager
async def lifespan(_app: FastAPI):
    classifier.load()
    yield


app = FastAPI(
    title="Municipal Issue AI Service",
    version="1.0.0",
    lifespan=lifespan,
)

MAX_IMAGE_BYTES = 10 * 1024 * 1024
Image.MAX_IMAGE_PIXELS = 40_000_000
ALLOWED_IMAGE_TYPES = {
    "image/jpeg": frozenset({".jpg", ".jpeg"}),
    "image/png": frozenset({".png"}),
}
DECODED_FORMAT_BY_MIME = {
    "image/jpeg": "JPEG",
    "image/png": "PNG",
}


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/predict", response_model=PredictionResponse)
def predict(image: UploadFile = File(...)) -> PredictionResponse:
    allowed_extensions = ALLOWED_IMAGE_TYPES.get(image.content_type or "")
    if allowed_extensions is None:
        raise HTTPException(status_code=400, detail="Only JPEG and PNG images are supported")
    extension = Path(image.filename or "").suffix.casefold()
    if extension not in allowed_extensions:
        raise HTTPException(
            status_code=400,
            detail="Image extension does not match its declared type",
        )
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
    if decoded.format != DECODED_FORMAT_BY_MIME[image.content_type]:
        raise HTTPException(
            status_code=400,
            detail="Image content does not match its declared type",
        )

    prediction = classifier.classify(decoded)
    if prediction.issue_type not in ISSUE_CATEGORIES:
        raise HTTPException(status_code=502, detail="Model returned an unsupported issue type")
    confidence_level = confidence_policy.level(prediction.issue_score)
    return PredictionResponse(
        issueType=None if confidence_level == "LOW" else prediction.issue_type,
        candidateIssueType=prediction.issue_type,
        confidence=prediction.issue_score,
        confidenceLevel=confidence_level,
        category=ISSUE_CATEGORIES[prediction.issue_type],
        suggestedSeverity=prediction.severity,
        requiresManualReview=confidence_level == "LOW",
    )
