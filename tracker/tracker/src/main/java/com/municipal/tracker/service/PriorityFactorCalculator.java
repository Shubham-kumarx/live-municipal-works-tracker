package com.municipal.tracker.service;

import com.municipal.tracker.config.PriorityProperties;
import com.municipal.tracker.model.ComplaintSeverity;
import com.municipal.tracker.model.ProjectImpactLevel;
import com.municipal.tracker.model.ProjectStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Comparator;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Component
@RequiredArgsConstructor
public class PriorityFactorCalculator {
    private final PriorityProperties properties;

    public FactorResult severity(Collection<ComplaintSeverity> severities) {
        ComplaintSeverity highest = severities == null ? null : severities.stream()
                .filter(java.util.Objects::nonNull)
                .max(Comparator.comparingDouble(this::levelScore))
                .orElse(null);
        if (highest == null) {
            return FactorResult.unavailable("No linked complaint severity is available");
        }
        double score = levelScore(highest);
        return FactorResult.available(highest.name(), score,
                "Highest linked complaint severity is " + highest.name());
    }

    public FactorResult complaintVolume(long linkedComplaintCount) {
        long safeCount = Math.max(0L, linkedComplaintCount);
        double score = (Math.min(safeCount, properties.getComplaintSaturationCount()) * 100.0)
                / properties.getComplaintSaturationCount();
        return FactorResult.available(Long.toString(safeCount), score,
                safeCount + " complaint" + (safeCount == 1 ? " is" : "s are") + " linked to this project");
    }

    public FactorResult impact(ProjectImpactLevel impactLevel) {
        if (impactLevel == null) {
            return FactorResult.unavailable("Project impact has not been classified");
        }
        double score = switch (impactLevel) {
            case LOW -> properties.getLowFactorScore();
            case MEDIUM -> properties.getMediumFactorScore();
            case HIGH -> properties.getHighFactorScore();
            case CRITICAL -> properties.getCriticalFactorScore();
        };
        return FactorResult.available(impactLevel.name(), score,
                "Recorded project impact is " + impactLevel.name());
    }

    public FactorResult deadlineRisk(ProjectStatus status, LocalDate expectedEndDate, LocalDate calculationDate) {
        if (status == ProjectStatus.COMPLETED || status == ProjectStatus.CANCELLED) {
            return FactorResult.available(status.name(), 0.0,
                    "Closed projects have no active deadline risk");
        }
        if (expectedEndDate == null) {
            return FactorResult.unavailable("Expected end date is missing");
        }
        if (calculationDate == null) {
            throw new IllegalArgumentException("Calculation date is required");
        }

        long daysRemaining = ChronoUnit.DAYS.between(calculationDate, expectedEndDate);
        if (daysRemaining <= 0) {
            String explanation = daysRemaining < 0
                    ? "Project is overdue by " + Math.abs(daysRemaining) + " day(s)"
                    : "Project is due today";
            return FactorResult.available(expectedEndDate.toString(), 100.0, explanation);
        }

        int horizon = properties.getDeadlineRiskHorizonDays();
        double score = daysRemaining >= horizon ? 0.0 : ((horizon - daysRemaining) * 100.0) / horizon;
        return FactorResult.available(expectedEndDate.toString(), score,
                "Project deadline is in " + daysRemaining + " day(s)");
    }

    public FactorResult expectedProgress(ProjectStatus status, LocalDate startDate,
                                         LocalDate expectedEndDate, LocalDate calculationDate) {
        if (status == ProjectStatus.COMPLETED) {
            return FactorResult.available("100", 100.0, "Completed project expected progress is 100%");
        }
        if (startDate == null || expectedEndDate == null) {
            return FactorResult.unavailable("Start date and expected end date are required for expected progress");
        }
        if (calculationDate == null) {
            throw new IllegalArgumentException("Calculation date is required");
        }
        if (expectedEndDate.isBefore(startDate)) {
            return FactorResult.unavailable("Expected end date is before the start date");
        }
        if (calculationDate.isBefore(startDate)) {
            return FactorResult.available("0", 0.0, "Project has not reached its start date");
        }
        if (startDate.equals(expectedEndDate)) {
            return FactorResult.available("100", 100.0,
                    "Same-day project has reached its scheduled date");
        }
        if (!calculationDate.isBefore(expectedEndDate)) {
            return FactorResult.available("100", 100.0,
                    calculationDate.isAfter(expectedEndDate)
                            ? "Project is past its expected end date"
                            : "Project has reached its expected end date");
        }

        long totalDays = ChronoUnit.DAYS.between(startDate, expectedEndDate);
        long elapsedDays = ChronoUnit.DAYS.between(startDate, calculationDate);
        double expected = (elapsedDays * 100.0) / totalDays;
        return FactorResult.available(formatNumber(expected), expected,
                "Expected progress based on elapsed scheduled time is " + formatNumber(expected) + "%");
    }

    public FactorResult progressGap(FactorResult expectedProgress, Integer actualProgress) {
        if (expectedProgress == null || !expectedProgress.available() || expectedProgress.normalizedScore() == null) {
            return FactorResult.unavailable("Progress gap is unavailable because expected progress is unavailable");
        }
        if (actualProgress == null) {
            return FactorResult.unavailable("Actual project progress is missing");
        }

        double safeActual = FactorResult.clamp(actualProgress.doubleValue());
        double gap = Math.max(0.0, expectedProgress.normalizedScore() - safeActual);
        return FactorResult.available(
                "expected=" + formatNumber(expectedProgress.normalizedScore())
                        + ", actual=" + formatNumber(safeActual),
                gap,
                gap == 0.0
                        ? "Project is on or ahead of its expected progress"
                        : "Project is behind expected progress by " + formatNumber(gap) + " percentage points");
    }

    private double levelScore(ComplaintSeverity severity) {
        return switch (severity) {
            case LOW -> properties.getLowFactorScore();
            case MEDIUM -> properties.getMediumFactorScore();
            case HIGH -> properties.getHighFactorScore();
            case CRITICAL -> properties.getCriticalFactorScore();
        };
    }

    public record FactorResult(boolean available, String rawValue, Double normalizedScore, String explanation) {
        static FactorResult available(String rawValue, double normalizedScore, String explanation) {
            return new FactorResult(true, rawValue, clamp(normalizedScore), explanation);
        }

        static FactorResult unavailable(String explanation) {
            return new FactorResult(false, null, null, explanation);
        }

        private static double clamp(double value) {
            return Math.max(0.0, Math.min(100.0, value));
        }
    }

    private static String formatNumber(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", FactorResult.clamp(value));
    }
}
