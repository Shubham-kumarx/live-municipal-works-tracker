# Live Municipal Works Tracker

The application consists of a React/Vite frontend, Spring Boot API, PostgreSQL database,
and a local FastAPI computer-vision service for optional complaint image analysis.

## AI-assisted complaints

The browser sends complaint images only to Spring Boot. Spring validates the image and
calls FastAPI internally at `AI_SERVICE_BASE_URL` (default `http://127.0.0.1:8000`). The
citizen must confirm or edit every result, and manual submission remains available when
the AI service is offline.

See `ai-service/README.md` for local AI service setup and `docs/ai-model-decision.md` for
the model, supported taxonomy, score interpretation, sources, and limitations.
