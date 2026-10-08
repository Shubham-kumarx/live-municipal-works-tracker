# Phase 12 Follow-up Manual Actions

## Action 1
Reason: Production databases may disable Hibernate automatic schema updates.
Exact manual step: Review and apply the additive Phase 12 follow-up schema before deploying the updated backend when automatic schema updates are disabled.
Commands/UI steps: Use the approved PostgreSQL administration process to run `artifacts/codex/phase-12-follow-up-schema.sql` against the intended database. Do not run it against an unrelated database.
Expected result: The `login_attempts` and `project_flags` tables and their indexes exist; existing tables and records remain unchanged.
How to verify: Inspect the PostgreSQL schema and confirm both tables, the unique project/citizen constraint, foreign keys, and indexes exist.
Required before next phase: NO
Rollback: Leave the additive tables unused if the application change is rolled back. Do not drop them unless their data has been reviewed and deletion is separately approved.

## Action 2
Reason: Authentication cookies must use the Secure attribute in an HTTPS production deployment.
Exact manual step: Set `AUTH_COOKIE_SECURE=true` in the production backend environment.
Commands/UI steps: Configure the environment variable through the deployment platform's secret/configuration interface, then restart the backend. Do not place it in source control.
Expected result: Login responses issue the `municipal_auth` cookie with `HttpOnly`, `Secure`, `SameSite=Strict`, and `Path=/`.
How to verify: In browser developer tools on the HTTPS deployment, inspect the login response and stored cookie attributes.
Required before next phase: NO
Rollback: Remove the environment override only for a local HTTP development environment; keep it enabled for HTTPS production.
