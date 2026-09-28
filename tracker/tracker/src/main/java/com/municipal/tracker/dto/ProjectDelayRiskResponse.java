package com.municipal.tracker.dto;

import com.municipal.tracker.model.DelayRisk;
import com.municipal.tracker.service.DelayRiskService;

import java.time.LocalDateTime;

public record ProjectDelayRiskResponse(
        Long projectId,
        boolean available,
        DelayRisk delayRisk,
        Double expectedProgress,
        Double actualProgress,
        Double progressGap,
        boolean overdue,
        String reason,
        LocalDateTime calculatedAt) {

    public static ProjectDelayRiskResponse from(DelayRiskService.Calculation calculation) {
        return new ProjectDelayRiskResponse(
                calculation.project().getId(),
                calculation.available(),
                calculation.delayRisk(),
                calculation.expectedProgress(),
                calculation.actualProgress(),
                calculation.progressGap(),
                calculation.overdue(),
                calculation.reason(),
                calculation.calculatedAt());
    }
}
