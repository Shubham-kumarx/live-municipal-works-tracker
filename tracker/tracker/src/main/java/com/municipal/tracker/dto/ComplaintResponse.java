package com.municipal.tracker.dto;

import com.municipal.tracker.model.*;

import java.time.LocalDateTime;

public record ComplaintResponse(
        Long id,
        Long reportingUserId,
        String reportingUserName,
        String imageUrl,
        String description,
        String locationAddress,
        Double latitude,
        Double longitude,
        ComplaintIssueType aiPredictedIssueType,
        Double aiConfidence,
        String aiConfidenceLevel,
        ComplaintSeverity aiSuggestedSeverity,
        ComplaintIssueType finalIssueType,
        ComplaintSeverity finalSeverity,
        ComplaintPredictionState predictionState,
        ComplaintStatus status,
        Long municipalProjectId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ComplaintResponse from(Complaint complaint) {
        User reporter = complaint.getReportingUser();
        MunicipalProject project = complaint.getMunicipalProject();
        return new ComplaintResponse(
                complaint.getId(), reporter.getId(), reporter.getFullName(), imageUrl(complaint),
                complaint.getDescription(), complaint.getLocationAddress(), complaint.getLatitude(),
                complaint.getLongitude(), complaint.getAiPredictedIssueType(), complaint.getAiConfidence(),
                complaint.getAiConfidenceLevel(), complaint.getAiSuggestedSeverity(),
                complaint.getFinalIssueType(), complaint.getFinalSeverity(), complaint.getPredictionState(),
                complaint.getStatus(), project == null ? null : project.getId(), complaint.getCreatedAt(),
                complaint.getUpdatedAt());
    }

    private static String imageUrl(Complaint complaint) {
        return complaint.getImageUrl() == null ? null
                : "/api/complaints/" + complaint.getId() + "/image";
    }
}
