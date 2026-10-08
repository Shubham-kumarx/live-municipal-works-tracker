package com.municipal.tracker.dto;

import com.municipal.tracker.model.ComplaintIssueType;
import com.municipal.tracker.model.ComplaintPredictionState;
import com.municipal.tracker.model.ComplaintSeverity;
import jakarta.validation.constraints.*;

public record ComplaintCreateRequest(
        @NotBlank(message = "Description is required")
        @Size(max = 2000, message = "Description must not exceed 2000 characters")
        String description,

        @NotBlank(message = "Location is required")
        @Size(max = 255, message = "Location must not exceed 255 characters")
        String locationAddress,

        @DecimalMin(value = "-90.0", message = "Latitude must be at least -90")
        @DecimalMax(value = "90.0", message = "Latitude must not exceed 90")
        Double latitude,

        @DecimalMin(value = "-180.0", message = "Longitude must be at least -180")
        @DecimalMax(value = "180.0", message = "Longitude must not exceed 180")
        Double longitude,

        ComplaintIssueType aiPredictedIssueType,

        @DecimalMin(value = "0.0", message = "AI confidence must be at least 0")
        @DecimalMax(value = "1.0", message = "AI confidence must not exceed 1")
        Double aiConfidence,

        AIAnalysisResponse.ConfidenceLevel aiConfidenceLevel,
        ComplaintSeverity aiSuggestedSeverity,

        @NotNull(message = "Final issue type is required")
        ComplaintIssueType finalIssueType,

        @NotNull(message = "Final severity is required")
        ComplaintSeverity finalSeverity,

        @NotNull(message = "Prediction confirmation state is required")
        ComplaintPredictionState predictionState
) {
    private static final String SUPPORTED_TYPES =
            "DOMESTIC_TRASH, ILLEGAL_PARKING, DAMAGED_SIGN, or POTHOLE";

    @AssertTrue(message = "AI predicted issue type must be " + SUPPORTED_TYPES)
    public boolean isAiPredictedIssueTypeSupported() {
        return aiPredictedIssueType == null || aiPredictedIssueType.isSupportedClassification();
    }

    @AssertTrue(message = "Final issue type must be " + SUPPORTED_TYPES)
    public boolean isFinalIssueTypeSupported() {
        return finalIssueType == null || finalIssueType.isSupportedClassification();
    }
}
