# Municipal Issue AI Service

This FastAPI service performs local computer-vision inference for the Spring Boot
application. Browser clients must use the Spring Boot complaint endpoints and must not
call this service directly.

## Local setup

From `ai-service/`:

```powershell
python -m venv .venv
.venv\Scripts\python.exe -m pip install -r requirements.txt
.venv\Scripts\python.exe -m uvicorn app:app --host 127.0.0.1 --port 8000
```

The service health endpoint is `GET /health`. Model files are stored in
`ai-service/.model-cache/` and are excluded from Git.

Optional confidence settings:

```text
AI_MEDIUM_CONFIDENCE_THRESHOLD=0.45
AI_HIGH_CONFIDENCE_THRESHOLD=0.70
```

These values classify a relative CLIP prompt score; they are not accuracy guarantees.
They must satisfy `0 <= medium <= high <= 1`. A score below the medium threshold is
returned as a tentative candidate and requires manual classification.
