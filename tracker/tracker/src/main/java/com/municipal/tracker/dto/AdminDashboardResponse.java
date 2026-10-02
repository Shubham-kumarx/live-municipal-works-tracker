package com.municipal.tracker.dto;

import com.municipal.tracker.model.ComplaintIssueType;
import com.municipal.tracker.model.ComplaintPredictionState;
import com.municipal.tracker.model.ComplaintSeverity;
import com.municipal.tracker.model.ComplaintStatus;
import com.municipal.tracker.model.DelayRisk;
import com.municipal.tracker.model.ProjectPriorityLevel;
import com.municipal.tracker.model.ProjectStatus;
import com.municipal.tracker.model.ProjectType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record AdminDashboardResponse(
        WorkMetrics workMetrics,
        ComplaintMetrics complaintMetrics,
        List<WorkDecisionRow> works,
        List<RecentComplaint> recentComplaints,
        List<DistributionItem> workStatusDistribution,
        List<DistributionItem> complaintSeverityDistribution,
        LocalDateTime generatedAt
) {
    public record WorkMetrics(
            long total,
            long active,
            long completed,
            long delayed,
            long highPriority,
            long highDelayRisk
    ) {
    }

    public record ComplaintMetrics(
            long total,
            long unresolved,
            long aiAssisted
    ) {
    }

    public record WorkDecisionRow(
            Long id,
            String projectName,
            ProjectType projectType,
            ProjectStatus status,
            String locationAddress,
            Long wardId,
            String wardName,
            String assignedWorkerName,
            LocalDate expectedEndDate,
            Integer progressPercentage,
            ProjectPriorityLevel priorityLevel,
            double priorityScore,
            boolean delayRiskAvailable,
            DelayRisk delayRisk,
            Double expectedProgress,
            Double progressGap,
            String delayRiskReason,
            long linkedComplaintCount
    ) {
    }

    public record RecentComplaint(
            Long id,
            String description,
            String locationAddress,
            ComplaintIssueType issueType,
            ComplaintSeverity severity,
            ComplaintStatus status,
            ComplaintPredictionState predictionState,
            boolean aiAssisted,
            Long municipalProjectId,
            String municipalProjectName,
            LocalDateTime createdAt
    ) {
    }

    public record DistributionItem(String label, long count) {
    }
}
