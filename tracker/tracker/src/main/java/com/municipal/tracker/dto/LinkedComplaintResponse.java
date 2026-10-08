package com.municipal.tracker.dto;

import com.municipal.tracker.model.*;
import java.time.LocalDateTime;

public record LinkedComplaintResponse(
        Long id,
        String imageUrl,
        String description,
        String locationAddress,
        ComplaintIssueType finalIssueType,
        ComplaintSeverity finalSeverity,
        ComplaintStatus status,
        LocalDateTime createdAt) {
    public static LinkedComplaintResponse from(Complaint complaint) {
        String protectedImageUrl = complaint.getImageUrl() == null ? null
                : "/api/complaints/" + complaint.getId() + "/image";
        return new LinkedComplaintResponse(complaint.getId(), protectedImageUrl,
                complaint.getDescription(), complaint.getLocationAddress(), complaint.getFinalIssueType(),
                complaint.getFinalSeverity(), complaint.getStatus(), complaint.getCreatedAt());
    }
}
