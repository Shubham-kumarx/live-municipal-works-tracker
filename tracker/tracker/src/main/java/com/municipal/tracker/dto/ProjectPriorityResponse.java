package com.municipal.tracker.dto;

import com.municipal.tracker.model.ProjectPriorityLevel;
import com.municipal.tracker.service.PriorityCalculationService;

import java.time.LocalDateTime;
import java.util.List;

public record ProjectPriorityResponse(
        Long projectId,
        String projectName,
        double totalScore,
        ProjectPriorityLevel priorityLevel,
        String advisoryNotice,
        List<FactorScore> factors,
        List<String> reasons,
        LocalDateTime calculatedAt) {

    private static final String ADVISORY_NOTICE =
            "Advisory project score for decision support; this is not an official government formula.";

    public static ProjectPriorityResponse from(PriorityCalculationService.Calculation calculation) {
        return new ProjectPriorityResponse(
                calculation.project().getId(),
                calculation.project().getProjectName(),
                calculation.totalScore(),
                calculation.priorityLevel(),
                ADVISORY_NOTICE,
                calculation.factors().stream().map(FactorScore::from).toList(),
                List.copyOf(calculation.reasons()),
                calculation.calculatedAt());
    }

    public record FactorScore(
            String name,
            boolean available,
            String rawValue,
            Double normalizedScore,
            double weight,
            double weightedContribution,
            String explanation) {
        private static FactorScore from(PriorityCalculationService.FactorContribution factor) {
            return new FactorScore(factor.name(), factor.available(), factor.rawValue(),
                    factor.normalizedScore(), factor.weight(), factor.weightedContribution(),
                    factor.explanation());
        }
    }
}
