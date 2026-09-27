package com.municipal.tracker.dto;

import com.municipal.tracker.model.ComplaintCategory;
import com.municipal.tracker.model.ComplaintIssueType;
import com.municipal.tracker.model.ComplaintSeverity;

public record AIAnalysisResponse(
        ComplaintIssueType issueType,
        ComplaintIssueType candidateIssueType,
        double confidence,
        ConfidenceLevel confidenceLevel,
        ComplaintCategory category,
        ComplaintSeverity suggestedSeverity,
        boolean requiresManualReview
) {
    public enum ConfidenceLevel { LOW, MEDIUM, HIGH }
}
