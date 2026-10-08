from typing import Literal

from pydantic import BaseModel, ConfigDict, Field

IssueType = Literal[
    "DOMESTIC_TRASH",
    "ILLEGAL_PARKING",
    "DAMAGED_SIGN",
    "POTHOLE",
]


class PredictionResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    issue_type: IssueType | None = Field(alias="issueType")
    candidate_issue_type: IssueType = Field(alias="candidateIssueType")
    confidence: float = Field(ge=0.0, le=1.0)
    confidence_level: Literal["LOW", "MEDIUM", "HIGH"] = Field(alias="confidenceLevel")
    category: str
    suggested_severity: str = Field(alias="suggestedSeverity")
    requires_manual_review: bool = Field(alias="requiresManualReview")
