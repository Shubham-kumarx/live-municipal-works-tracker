# Phase 14 Manual Actions

## Action 1
Reason: The repository has no browser automation framework, so the rendered image preview and final visual layout could not be verified automatically. The API flow and frontend unit/build checks completed successfully.
Exact manual step: Open the running application in a browser and visually verify the citizen and administrator screens using the Phase 14 test accounts communicated separately.
Commands/UI steps:
```text
1. Start PostgreSQL, Spring Boot, FastAPI, and Vite using the repository's documented commands and environment variables.
2. Sign in as the Phase 14 citizen and open Report Issue.
3. Select artifacts/codex/Pothole.jpg and confirm that its preview appears.
4. Select Analyze and confirm that the prediction type, confidence, and severity appear.
5. Sign in as the Phase 14 administrator and open complaint management.
6. Confirm that complaint 1 shows its linked work, then open project 1 on the map and confirm its priority and delay-risk indicators.
```
Expected result: The image preview renders without a blank screen; prediction data is readable; complaint 1 shows its association with project 1; and project 1 displays CRITICAL priority and HIGH_DELAY_RISK.
How to verify: Check the browser console for uncaught errors and compare the displayed values with complaint ID 1 and project ID 1 returned by the backend APIs.
Required before next phase: NO
Rollback: No persistent change is required. Stop the development services with `Ctrl+C` after verification.
