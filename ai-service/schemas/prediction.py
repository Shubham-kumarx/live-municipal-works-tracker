from typing import Literal

from pydantic import BaseModel, ConfigDict, Field


class PredictionResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    issue_type: str | None = Field(alias="issueType")
    candidate_issue_type: str = Field(alias="candidateIssueType")
    confidence: float = Field(ge=0.0, le=1.0)
    confidence_level: Literal["LOW", "MEDIUM", "HIGH"] = Field(alias="confidenceLevel")
    category: str
    suggested_severity: str = Field(alias="suggestedSeverity")
    requires_manual_review: bool = Field(alias="requiresManualReview")
