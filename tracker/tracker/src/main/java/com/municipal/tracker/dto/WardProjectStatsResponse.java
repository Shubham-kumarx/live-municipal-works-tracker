package com.municipal.tracker.dto;

public record WardProjectStatsResponse(
        long totalProjects,
        long sanctioned,
        long inProgress,
        long completed,
        long delayed,
        double totalBudgetAllocated,
        double totalBudgetSpent) {
}
