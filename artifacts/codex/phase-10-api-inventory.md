# Phase 10 API Inventory

This inventory records the API surface implemented by the Spring controllers. Authorization combines the HTTP rules in `SecurityConfig` with method-level `@PreAuthorize` checks. Responses are returned directly without a shared success envelope.

## Authentication

| Method | Path | Authorization | Request | Success response |
|---|---|---|---|---|
| POST | `/api/auth/register` | Public | JSON `CitizenRegisterRequest` | `201 AuthResponse` |
| POST | `/api/auth/register-staff` | `MUNICIPAL_ADMIN`, `WARD_OFFICER` | JSON `RegisterRequest` | `201 AuthResponse` |
| POST | `/api/auth/login` | Public | JSON `LoginRequest` | `200 AuthResponse` |

## Wards

| Method | Path | Authorization | Request | Success response |
|---|---|---|---|---|
| GET | `/api/wards` | Public | None | `200 List<WardResponse>` |
| GET | `/api/wards/{id}` | Public | Path `id` | `200 WardResponse`; `404` when absent |
| GET | `/api/wards/city/{city}` | Public | Path `city` | `200 List<WardResponse>` |
| GET | `/api/wards/number/{wardNumber}` | Public | Path `wardNumber` | `200 WardResponse`; `404` when absent |
| POST | `/api/wards` | `MUNICIPAL_ADMIN` | JSON `WardWriteRequest` | `201 WardResponse` |
| PUT | `/api/wards/{id}` | `MUNICIPAL_ADMIN`, `WARD_OFFICER`; ward scope enforced in service | Path `id`, JSON `WardWriteRequest` | `200 WardResponse` |
| DELETE | `/api/wards/{id}` | `MUNICIPAL_ADMIN` | Path `id` | Empty `200` |

## Municipal Projects

| Method | Path | Authorization | Request | Success response |
|---|---|---|---|---|
| GET | `/api/projects/ward/{wardId}` | Public | Path `wardId` | `200 List<ProjectResponse>` |
| GET | `/api/projects/{id}` | Authenticated | Path `id` | `200 ProjectResponse`; `404` when absent |
| GET | `/api/projects/ward/{wardId}/stats` | Public | Path `wardId` | `200 WardProjectStatsResponse` |
| GET | `/api/projects/ward/{wardId}/flagged` | `WARD_OFFICER`, `MUNICIPAL_ADMIN`, `AUDITOR`; ward scope enforced | Path `wardId` | `200 List<ProjectResponse>` |
| GET | `/api/projects/my-projects` | `FIELD_WORKER` | Authenticated principal | `200 List<ProjectResponse>` |
| POST | `/api/projects/ward/{wardId}` | `MUNICIPAL_ADMIN`, `WARD_OFFICER`; ward scope enforced | Path `wardId`, JSON `ProjectCreateRequest` | `201 ProjectResponse` |
| PATCH | `/api/projects/{id}/status` | `FIELD_WORKER`, `WARD_OFFICER`, `MUNICIPAL_ADMIN`; assignment/ward scope enforced | Path `id`, JSON `ProjectStatusUpdateRequest` | `200 ProjectResponse` |
| PATCH | `/api/projects/{projectId}/assign/{workerId}` | `WARD_OFFICER`, `MUNICIPAL_ADMIN`; ward scope enforced | Path `projectId`, path `workerId` | `200 ProjectResponse` |
| PATCH | `/api/projects/{id}/flag` | Any authenticated user | Path `id` | `200 ProjectResponse` |
| PATCH | `/api/projects/{id}/budget` | `WARD_OFFICER`, `MUNICIPAL_ADMIN`; ward scope enforced | Path `id`, JSON `ProjectBudgetUpdateRequest` | `200 ProjectResponse` |
| PATCH | `/api/projects/{id}/impact` | `WARD_OFFICER`, `MUNICIPAL_ADMIN`; ward scope enforced | Path `id`, JSON `ProjectImpactUpdateRequest` | `200 ProjectResponse` |
| GET | `/api/projects/{id}/priority` | `CITIZEN`, `FIELD_WORKER`, `WARD_OFFICER`, `MUNICIPAL_ADMIN`, `AUDITOR`; ward scope enforced | Path `id` | `200 ProjectPriorityResponse` |
| GET | `/api/projects/{id}/delay-risk` | `CITIZEN`, `FIELD_WORKER`, `WARD_OFFICER`, `MUNICIPAL_ADMIN`, `AUDITOR`; ward scope enforced | Path `id` | `200 ProjectDelayRiskResponse` |
| GET | `/api/projects/{projectId}/complaints` | `CITIZEN`, `FIELD_WORKER`, `WARD_OFFICER`, `MUNICIPAL_ADMIN`, `AUDITOR`; ward scope enforced | Path `projectId` | `200 List<LinkedComplaintResponse>` |

## Project Photos

| Method | Path | Authorization | Request | Success response |
|---|---|---|---|---|
| POST | `/api/upload/project/{projectId}/photos` | `FIELD_WORKER`, `WARD_OFFICER`, `MUNICIPAL_ADMIN`; assignment/ward scope enforced | Path `projectId`, multipart `files` | `200 ProjectResponse` |

## Complaints and AI Analysis

| Method | Path | Authorization | Request | Success response |
|---|---|---|---|---|
| POST | `/api/complaints/analyze` | `CITIZEN` | Multipart `image` | `200 AIAnalysisResponse` |
| POST | `/api/complaints` | `CITIZEN` | Multipart JSON part `complaint` (`ComplaintCreateRequest`) and `image` | `201 ComplaintResponse` |
| GET | `/api/complaints` | `WARD_OFFICER`, `MUNICIPAL_ADMIN`; ward scope enforced | None | `200 List<ComplaintResponse>` |
| GET | `/api/complaints/linkable-projects` | `WARD_OFFICER`, `MUNICIPAL_ADMIN`; ward scope enforced | None | `200 List<ProjectLinkOptionResponse>` |
| GET | `/api/complaints/{complaintId}/project` | `WARD_OFFICER`, `MUNICIPAL_ADMIN`; ward scope enforced | Path `complaintId` | `200 ComplaintLinkedProjectResponse`; `204` when unlinked |
| PATCH | `/api/complaints/{complaintId}/project/{projectId}` | `WARD_OFFICER`, `MUNICIPAL_ADMIN`; ward scope enforced | Path `complaintId`, path `projectId` | `200 ComplaintResponse`; `409` when already linked |
| DELETE | `/api/complaints/{complaintId}/project` | `WARD_OFFICER`, `MUNICIPAL_ADMIN`; ward scope enforced | Path `complaintId` | `200 ComplaintResponse`; `409` when not linked |

## Administrative Dashboard

| Method | Path | Authorization | Request | Success response |
|---|---|---|---|---|
| GET | `/api/dashboard` | `MUNICIPAL_ADMIN`, `WARD_OFFICER`; ward scope enforced | None | `200 AdminDashboardResponse` |

## Shared Error Statuses

| Status | Use |
|---|---|
| `400 Bad Request` | DTO validation, unreadable JSON/enums, constraint violations, invalid domain input |
| `401 Unauthorized` | Invalid login credentials or missing/invalid authentication where required |
| `403 Forbidden` | Role, project assignment, or ward-scope denial |
| `404 Not Found` | Requested project, complaint, ward, worker, or other required resource is absent |
| `409 Conflict` | Complaint relationship state conflicts |
| `413 Payload Too Large` | Multipart upload exceeds configured limits |
| `503 Service Unavailable` | AI analysis service is unavailable or returns an unusable response |
| `500 Internal Server Error` | Unexpected server failure with a sanitized message |

All centralized error bodies use the JSON shape `{ "message": "..." }`.
