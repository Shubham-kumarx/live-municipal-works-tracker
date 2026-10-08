# Admin Dashboard Metrics

The admin dashboard is decision-support software for this project. It uses Explainable Weighted Priority Scoring: configurable weighted rules rather than machine learning. The score is advisory and is not an official government formula.

## Access scope

- A municipal administrator sees projects and complaints across all wards.
- A ward officer sees projects in their assigned ward and complaints reported by users in that ward.

## Work metrics

- **Total:** every project in scope, including cancelled projects.
- **Active:** projects whose status is `IN_PROGRESS`.
- **Completed:** projects whose status is `COMPLETED`.
- **Delayed:** projects whose status is `DELAYED`.
- **High priority:** projects whose calculated priority is `HIGH` or `CRITICAL`.
- **High delay risk:** projects whose available Rule-Based Delay Risk Detection result is `HIGH_DELAY_RISK`.

Priority and Rule-Based Delay Risk Detection use the existing configured Phase 4 and Phase 5 calculations. Delay risk is threshold-based decision support, not a machine-learning forecast. An unavailable result is shown as unavailable and is not counted as high risk.

## Complaint metrics

- **Total complaints:** every complaint in scope.
- **Unresolved complaints:** complaints whose status is not `RESOLVED`. The current model only defines `SUBMITTED`, so all current complaints are unresolved.
- **AI-assisted complaints:** complaints with a persisted non-null `aiPredictedIssueType`, including predictions that the citizen later edited or rejected.

## Distributions

- Work status distribution counts every project by its persisted `ProjectStatus`.
- Complaint severity distribution counts every complaint by its final, user-confirmed `ComplaintSeverity`.

