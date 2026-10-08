# Phase 15 Database Migration and Data-Preservation Audit

## Audit Scope

- Repository: `live-municipal-works-tracker`
- Backend database: PostgreSQL 18.6, database `municipal_db`, schema `public`
- ORM strategy: Spring Data JPA/Hibernate with `spring.jpa.hibernate.ddl-auto=update`
- Test strategy: H2 in PostgreSQL compatibility mode with `ddl-auto=create-drop`
- Migration framework: none (no Flyway or Liquibase dependency or migration directory)
- Catalog inspection: read-only queries against `information_schema` and `pg_catalog`

## T15.1 Entity and Schema Strategy Comparison

The live schema contains the six entity tables declared by the application plus the `project_photos` element-collection table:

| JPA model | Physical table | Identifier strategy | Result |
|---|---|---|---|
| `Ward` | `wards` | identity `bigint` | Present |
| `User` | `users` | identity `bigint` | Present |
| `MunicipalProject` | `municipal_projects` | identity `bigint` | Present |
| `Complaint` | `complaints` | identity `bigint` | Present |
| `ProjectFlag` | `project_flags` | identity `bigint` | Present |
| `LoginAttempt` | `login_attempts` | identity `bigint` | Present |
| `MunicipalProject.photoUrls` | `project_photos` | project foreign key | Present |

All entity fields and join columns inspected in the source have corresponding live columns. Hibernate has also generated PostgreSQL check constraints for enum-backed columns. No unexpected application table was found in the `public` schema.

The current `ddl-auto=update` setting explains how the schema reached the current shape, but it is not a reproducible migration history: it does not record ordering, data backfills, deployment review, or rollback steps. The H2 test configuration verifies entity mappings against a newly created schema, but does not prove that an existing PostgreSQL database can be migrated safely.

## T15.2 Table Inventory and Provenance

| Table | Introduced by | Classification |
|---|---|---|
| `wards` | Original ward model | Baseline |
| `users` | Original user model | Baseline |
| `municipal_projects` | Original project model | Baseline |
| `project_photos` | Original `MunicipalProject.photoUrls` collection | Baseline |
| `complaints` | Phase 3 complaint persistence | New feature table |
| `login_attempts` | Phase 12 security follow-up | New security table |
| `project_flags` | Phase 12 security follow-up | New feature/security table |

Git history confirms that `complaints` was introduced with the Phase 3 model, while `login_attempts` and `project_flags` were added in the Phase 12 follow-up. The existing `phase-12-follow-up-schema.sql` documents only the latter two tables. All three feature-added tables are present in the live database.

## T15.3 New Column Inventory

### Phase 3: `complaints`

`id`, `reporting_user_id`, `image_url`, `description`, `location_address`, `latitude`, `longitude`, `ai_predicted_issue_type`, `ai_confidence`, `ai_confidence_level`, `ai_suggested_severity`, `final_issue_type`, `final_severity`, `prediction_state`, `status`, `municipal_project_id`, `created_at`, and `updated_at`.

The optional `municipal_project_id` column existed in the initial Complaint entity and was later used by the Phase 6 linking workflow; it was not introduced as a separate many-to-many join table.

### Phase 4: `municipal_projects`

- `impact_level` (`varchar(255)`, nullable)

The existing `flagged` and `flag_count` project columns were part of the original project entity and are baseline columns, even though later security work added normalized per-citizen records in `project_flags`.

### Phase 12: `login_attempts`

`id`, `login_key`, `failure_count`, `window_started_at`, `blocked_until`, and `updated_at`.

### Phase 12: `project_flags`

`id`, `project_id`, `citizen_id`, and `created_at`.

Every listed column is present in the live PostgreSQL schema with the type and nullability resolved from the current entity metadata.

## T15.4 Foreign-Key Inventory

| Owning table/column | Referenced table | Nullable | Provenance |
|---|---|---:|---|
| `users.ward_id` | `wards.id` | Yes | Baseline |
| `municipal_projects.ward_id` | `wards.id` | No | Baseline |
| `municipal_projects.assigned_worker_id` | `users.id` | Yes | Baseline |
| `municipal_projects.created_by_id` | `users.id` | No | Baseline |
| `project_photos.project_id` | `municipal_projects.id` | No | Baseline |
| `complaints.reporting_user_id` | `users.id` | No | Phase 3 |
| `complaints.municipal_project_id` | `municipal_projects.id` | Yes | Phase 3 / used by Phase 6 |
| `project_flags.project_id` | `municipal_projects.id` | No | Phase 12 |
| `project_flags.citizen_id` | `users.id` | No | Phase 12 |

All nine foreign keys are present. None declares cascading update or delete behavior, so PostgreSQL uses `NO ACTION`. This preserves related records by rejecting deletion of a referenced ward, user, or project while dependent rows exist. Complaint linking correctly uses one nullable foreign key and does not introduce a many-to-many join table.

## T15.5 Existing-Data Compatibility

The read-only audit covered 1 ward, 4 users, 1 municipal project, 1 complaint, and currently empty `project_photos`, `login_attempts`, and `project_flags` tables.

All compatibility checks returned zero affected rows:

- orphaned ward, user, project, photo, complaint, or flag relationships;
- duplicate ward numbers, user emails, login keys, or project/citizen flag pairs;
- progress outside 0–100;
- negative allocated or spent budgets;
- expected or actual end dates before the project start date;
- disagreement between the denormalized `flagged` and `flag_count` values;
- AI confidence outside 0–1;
- manual complaints carrying AI-only fields;
- AI-assisted complaints missing required AI prediction fields.

The current development records are compatible with the present mappings and constraints. No data repair, deletion, or backfill is required for the inspected database.

## T15.6 Nullability and Defaults

Entity nullability matches the live PostgreSQL columns. The feature-added nullable fields are intentional: optional AI output, complaint coordinates, complaint-project association, login block expiry, and project impact. Required complaint classification, ownership, image, location, status, and timestamps are non-null in both JPA and PostgreSQL.

Database defaults exist for baseline project `budget_spent`, `progress_percentage`, `flagged`, and `flag_count`, and for baseline ward/user `active`. Lifecycle callbacks initialize the same values for ORM inserts. Complaint status/timestamps and login/flag timestamps are assigned by application lifecycle logic rather than database defaults; this is consistent with current application-only writes, but direct SQL inserts must provide those required values.

The safe migration keeps `impact_level` nullable because historical projects have no defensible impact classification to backfill. New feature tables are created with their final nullability because they contain no legacy rows when migrating from the original baseline. No existing column is tightened to `NOT NULL`, and no fabricated classification or timestamp value is introduced.

## T15.7 Enum Persistence

Every persisted enum uses `@Enumerated(EnumType.STRING)`, so enum reordering cannot corrupt stored meaning. The persisted enums are:

- `Role` in `users.role`;
- `ProjectType`, `ProjectStatus`, and nullable `ProjectImpactLevel` in `municipal_projects`;
- `ComplaintIssueType`, `ComplaintSeverity`, `ComplaintPredictionState`, and `ComplaintStatus` in `complaints`.

`ComplaintCategory`, `ProjectPriorityLevel`, and `DelayRisk` are calculated/transport values and are not stored in database columns.

Distinct-value checks found only constants supported by the current Java enums. The inspected values were citizen/admin roles, road repair, sanctioned status, critical impact, road-crack AI prediction, high AI/final severity, pothole final type, edited prediction state, and submitted complaint status. No unknown or legacy enum token was found.

PostgreSQL check constraints currently restrict all persisted enum columns to their current Java constant sets. Future enum additions must update both the Java enum and the corresponding database check constraint in the same migration.

## T15.8 Index Review

The live database has 16 indexes. It correctly contains all entity-declared complaint, login-attempt, and project-flag indexes plus primary and unique indexes. PostgreSQL does not automatically index the referencing side of foreign keys, and the baseline tables currently lack indexes for frequent ward, worker, role, project-date, and photo lookup paths.

The safe migration therefore adds these evidence-based indexes:

- `(users.ward_id, users.role)` for ward staff lists and ward/role filtering;
- `users.role` for global role lists;
- `(municipal_projects.ward_id, created_at DESC)` for ward map/list queries;
- `(municipal_projects.ward_id, status)` for ward status filters and counts;
- `(municipal_projects.ward_id, project_type)` for ward type filtering;
- `municipal_projects.assigned_worker_id` for worker assignments;
- `municipal_projects.created_by_id` to support its foreign key;
- a partial ward index for flagged projects;
- `project_photos.project_id` to support its foreign key and collection loading;
- `complaints.created_at DESC` for the real recent-complaint query.

Existing complaint user/project indexes already support their foreign keys and filtered retrieval. Existing unique indexes cover ward number, email, login key, and the project/citizen flag pair. No index is proposed for low-selectivity boolean columns without an applicable partial predicate.

## T15.9 Migration Strategy

The project has no migration framework, so this phase does not add Flyway or Liquibase late in the project lifecycle. `phase-15-safe-migration.sql` is the canonical additive migration from the original ward/user/project schema to the current feature schema.

The script:

- creates the three feature tables only when absent;
- adds the nullable impact column only when absent;
- adds the impact enum constraint through a catalog-guarded block;
- creates only missing indexes;
- preserves all existing records;
- contains no table/database drop, truncation, record deletion, or destructive column conversion;
- is intended to run through `psql` with `--single-transaction` and `ON_ERROR_STOP=1` so any failure rolls back the whole migration.

Before a deployment, take a PostgreSQL backup, run the compatibility queries documented in this audit, apply the migration in one transaction, start the application once with `spring.jpa.hibernate.ddl-auto=validate`, and retain `validate` for controlled environments. The repository configuration remains unchanged in this audit because switching from `update` before every target database is migrated could prevent startup.

For this development database, no migration execution is required: all current entity tables, columns, foreign keys, constraints, and feature indexes already exist, and all existing data passed compatibility checks. The additional recommended baseline indexes can be applied later through the reviewed script when preparing a deployment dataset large enough to benefit from them.

## Final Assessment

The inspected database is data-compatible with the current application and requires no repair. The material deployment risk is process-related: automatic Hibernate updates are not a migration history. The supplied SQL closes the missing baseline-to-current documentation gap without changing the live database during this audit.

## Verification Performed

- Queried PostgreSQL 18.6 `information_schema` and `pg_catalog` read-only for tables, columns, identity metadata, constraints, foreign keys, indexes, row counts, stored enums, and compatibility aggregates.
- Executed the complete migration inside `BEGIN` / `ROLLBACK` with `ON_ERROR_STOP`; every statement succeeded.
- Confirmed after rollback that zero recommended indexes from the validation transaction remained in the live schema.
- Scanned executable SQL and found no `DROP`, `TRUNCATE`, or `DELETE` statement.
- Ran the backend Maven suite: 140 tests, 0 failures, 0 errors, 0 skipped.









