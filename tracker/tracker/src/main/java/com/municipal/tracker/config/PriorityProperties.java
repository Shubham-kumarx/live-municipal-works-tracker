package com.municipal.tracker.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.priority")
public class PriorityProperties {
    private double severityWeight = 0.30;
    private double complaintVolumeWeight = 0.20;
    private double impactWeight = 0.20;
    private double deadlineRiskWeight = 0.15;
    private double progressGapWeight = 0.15;

    private double lowFactorScore = 25.0;
    private double mediumFactorScore = 50.0;
    private double highFactorScore = 75.0;
    private double criticalFactorScore = 100.0;

    private int complaintSaturationCount = 5;
    private int deadlineRiskHorizonDays = 30;

    private double mediumPriorityThreshold = 30.0;
    private double highPriorityThreshold = 55.0;
    private double criticalPriorityThreshold = 75.0;

    @PostConstruct
    public void validate() {
        validateUnitWeight(severityWeight, "severity-weight");
        validateUnitWeight(complaintVolumeWeight, "complaint-volume-weight");
        validateUnitWeight(impactWeight, "impact-weight");
        validateUnitWeight(deadlineRiskWeight, "deadline-risk-weight");
        validateUnitWeight(progressGapWeight, "progress-gap-weight");

        double totalWeight = severityWeight + complaintVolumeWeight + impactWeight
                + deadlineRiskWeight + progressGapWeight;
        if (Math.abs(totalWeight - 1.0) > 0.000001) {
            throw new IllegalStateException("Priority factor weights must total 1.0");
        }

        validateScore(lowFactorScore, "low-factor-score");
        validateScore(mediumFactorScore, "medium-factor-score");
        validateScore(highFactorScore, "high-factor-score");
        validateScore(criticalFactorScore, "critical-factor-score");
        if (!(lowFactorScore < mediumFactorScore && mediumFactorScore < highFactorScore
                && highFactorScore < criticalFactorScore)) {
            throw new IllegalStateException("Priority factor scores must be strictly increasing");
        }
        if (complaintSaturationCount <= 0) {
            throw new IllegalStateException("Complaint saturation count must be positive");
        }
        if (deadlineRiskHorizonDays <= 0) {
            throw new IllegalStateException("Deadline risk horizon must be positive");
        }

        validateScore(mediumPriorityThreshold, "medium-priority-threshold");
        validateScore(highPriorityThreshold, "high-priority-threshold");
        validateScore(criticalPriorityThreshold, "critical-priority-threshold");
        if (!(mediumPriorityThreshold < highPriorityThreshold
                && highPriorityThreshold < criticalPriorityThreshold)) {
            throw new IllegalStateException("Priority thresholds must be strictly increasing");
        }
    }

    private void validateUnitWeight(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalStateException(name + " must be between 0 and 1");
        }
    }

    private void validateScore(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 100.0) {
            throw new IllegalStateException(name + " must be between 0 and 100");
        }
    }
}
