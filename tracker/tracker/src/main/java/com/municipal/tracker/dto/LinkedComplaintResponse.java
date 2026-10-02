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
        return new LinkedComplaintResponse(complaint.getId(), complaint.getImageUrl(),
                complaint.getDescription(), complaint.getLocationAddress(), complaint.getFinalIssueType(),
                complaint.getFinalSeverity(), complaint.getStatus(), complaint.getCreatedAt());
    }
}
