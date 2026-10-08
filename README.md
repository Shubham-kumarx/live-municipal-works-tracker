# Smart Municipal Works Monitoring and Decision-Support System with AI-Assisted Issue Classification

The application consists of a React/Vite frontend, Spring Boot API, PostgreSQL database,
and a local FastAPI computer-vision service for optional complaint image analysis.

## Academic contribution

The project integrates citizen issue reporting, AI-assisted image classification, human
confirmation, complaint-to-work association, explainable weighted prioritization,
rule-based delay risk detection, live municipal work monitoring, and an administrative
decision-support dashboard in one system. Administrative users remain responsible for
associations, interpretation, and action.

## AI-assisted complaints

The browser sends complaint images only to Spring Boot. Spring validates the image and
calls FastAPI internally at `AI_SERVICE_BASE_URL` (default `http://127.0.0.1:8000`). The
citizen must confirm or edit every result, and manual submission remains available when
the AI service is offline.

AI output is advisory. It does not autonomously approve complaints, associate them with
municipal work, allocate resources, or make administrative decisions.

See `ai-service/README.md` for local AI service setup and `docs/ai-model-decision.md` for
the model, supported taxonomy, score interpretation, sources, and limitations.

## Explainable Weighted Priority Scoring

Projects receive an advisory score from five configured factors: linked-complaint
severity, linked-complaint volume, recorded impact, deadline risk, and progress gap. The
API exposes each normalized factor and weighted contribution. This is transparent
decision-support logic, not machine learning or an official government formula.

## Rule-Based Delay Risk Detection

The system compares expected progress, actual progress, their gap, project completion,
and deadline state against configured thresholds. The resulting risk level is an advisory
rule-based classification, not a machine-learning forecast.
