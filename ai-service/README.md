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

At first inference, the service checks the local cache without contacting the model
registry. If the cache is incomplete, it falls back to the standard model download.
After one successful download, later starts can load entirely from the local cache.
`AI_MODEL_ID`, when overridden, must identify a CLIP-compatible Transformers model.

Optional confidence settings:

```text
AI_MEDIUM_CONFIDENCE_THRESHOLD=0.45
AI_HIGH_CONFIDENCE_THRESHOLD=0.70
```

These values classify a relative CLIP prompt score; they are not accuracy guarantees.
They must satisfy `0 <= medium <= high <= 1`. A score below the medium threshold is
returned as a tentative candidate and requires manual classification.
