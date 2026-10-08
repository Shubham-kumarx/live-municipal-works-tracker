# Explainable Weighted Priority Scoring

## Purpose

This repository uses explainable weighted priority scoring to help users compare municipal projects from consistently recorded data. It is configurable decision-support logic, not machine learning. It is not an official government formula, policy, service-level target, or replacement for human review.

## Available Data

| Factor | Repository source | Availability |
| --- | --- | --- |
| Severity | Highest `Complaint.finalSeverity` among complaints linked to the project | Available only when a linked complaint has final severity |
| Complaint volume | Count of complaints whose `municipalProject` references the project | Available; zero when no complaint is linked |
| Impact | Explicit nullable `MunicipalProject.impactLevel` | Unavailable until an authorized user records it |
| Deadline risk | Project status and `expectedEndDate` | Unavailable when the expected end date is missing |
| Progress gap | Expected progress from project dates compared with `progressPercentage` | Unavailable when expected progress cannot be calculated |

Complaint links are never inferred from text, coordinates, issue type, or ward. The score uses only complaint relationships already persisted in the database. The current complaint workflow does not create project links, so complaint-derived factors remain empty until a later authorized workflow records those relationships.

Budget, flag count, project type, and location are not used as impact proxies. The repository has no population, traffic, beneficiary, or service-coverage data from which impact could be calculated defensibly.

## Weights

| Factor | Weight |
| --- | ---: |
| Severity | 30% |
| Complaint volume | 20% |
| Impact | 20% |
| Deadline risk | 15% |
| Progress gap | 15% |

Every normalized factor is bounded from 0 to 100. Its weighted contribution is `normalized score × weight`. Contributions are summed and the total is clamped from 0 to 100.

When a factor is unavailable, its contribution is zero and the API identifies it as unavailable with a reason. Its weight is not redistributed, so results calculated with different levels of data completeness remain comparable.

## Configurable Rules

- Severity and impact map `LOW`, `MEDIUM`, `HIGH`, and `CRITICAL` to 25, 50, 75, and 100.
- Complaint volume reaches 100 at the configured saturation count and cannot exceed 100.
- Deadline risk increases linearly inside the configured deadline horizon. An unfinished overdue project receives 100.
- Expected progress advances linearly between the start and expected end dates. This is an explicit simplifying assumption, not evidence that every project should progress uniformly.
- Progress gap is the positive difference between expected and actual progress. A project on or ahead of schedule receives zero.
- Priority thresholds and all calculation constants are centralized in application configuration.

## Interpretation Limits

The score reflects the completeness and quality of stored data. A missing impact classification or missing date lowers the available contribution and is shown in the explanation. A single critical complaint can determine the severity factor because the project severity uses the highest linked final severity. The configured mappings, saturation count, date horizon, and priority thresholds are transparent project choices and have not been validated as public-policy standards.
