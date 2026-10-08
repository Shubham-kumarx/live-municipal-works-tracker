package com.municipal.tracker;

import com.municipal.tracker.config.PriorityProperties;
import com.municipal.tracker.model.ComplaintSeverity;
import com.municipal.tracker.model.ProjectStatus;
import com.municipal.tracker.service.PriorityFactorCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PriorityFactorCalculatorTest {
    private PriorityFactorCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new PriorityFactorCalculator(new PriorityProperties());
    }

    @Test
    void mapsEverySeverityToConfiguredScore() {
        assertThat(calculator.severity(List.of(ComplaintSeverity.LOW)).normalizedScore()).isEqualTo(25.0);
        assertThat(calculator.severity(List.of(ComplaintSeverity.MEDIUM)).normalizedScore()).isEqualTo(50.0);
        assertThat(calculator.severity(List.of(ComplaintSeverity.HIGH)).normalizedScore()).isEqualTo(75.0);
        assertThat(calculator.severity(List.of(ComplaintSeverity.CRITICAL)).normalizedScore()).isEqualTo(100.0);
    }

    @Test
    void usesHighestLinkedComplaintSeverity() {
        PriorityFactorCalculator.FactorResult result = calculator.severity(
                List.of(ComplaintSeverity.LOW, ComplaintSeverity.CRITICAL, ComplaintSeverity.MEDIUM));

        assertThat(result.available()).isTrue();
        assertThat(result.rawValue()).isEqualTo("CRITICAL");
        assertThat(result.normalizedScore()).isEqualTo(100.0);
    }

    @Test
    void reportsSeverityUnavailableWithoutLinkedComplaintData() {
        PriorityFactorCalculator.FactorResult result = calculator.severity(List.of());

        assertThat(result.available()).isFalse();
        assertThat(result.normalizedScore()).isNull();
    }

    @Test
    void normalizesComplaintVolumeAgainstConfiguredSaturationCount() {
        assertThat(calculator.complaintVolume(0).normalizedScore()).isEqualTo(0.0);
        assertThat(calculator.complaintVolume(1).normalizedScore()).isEqualTo(20.0);
        assertThat(calculator.complaintVolume(3).normalizedScore()).isEqualTo(60.0);
        assertThat(calculator.complaintVolume(5).normalizedScore()).isEqualTo(100.0);
        assertThat(calculator.complaintVolume(12).normalizedScore()).isEqualTo(100.0);
    }

    @Test
    void treatsInvalidNegativeComplaintCountAsZero() {
        PriorityFactorCalculator.FactorResult result = calculator.complaintVolume(-3);

        assertThat(result.rawValue()).isEqualTo("0");
        assertThat(result.normalizedScore()).isEqualTo(0.0);
    }

    @Test
    void calculatesBoundedDeadlineRisk() {
        LocalDate today = LocalDate.of(2026, 9, 27);

        assertThat(calculator.deadlineRisk(ProjectStatus.IN_PROGRESS, today.minusDays(2), today)
                .normalizedScore()).isEqualTo(100.0);
        assertThat(calculator.deadlineRisk(ProjectStatus.IN_PROGRESS, today, today)
                .normalizedScore()).isEqualTo(100.0);
        assertThat(calculator.deadlineRisk(ProjectStatus.IN_PROGRESS, today.plusDays(15), today)
                .normalizedScore()).isEqualTo(50.0);
        assertThat(calculator.deadlineRisk(ProjectStatus.IN_PROGRESS, today.plusDays(30), today)
                .normalizedScore()).isEqualTo(0.0);
        assertThat(calculator.deadlineRisk(ProjectStatus.IN_PROGRESS, today.plusDays(90), today)
                .normalizedScore()).isEqualTo(0.0);
    }

    @Test
    void handlesClosedAndMissingDeadlineData() {
        LocalDate today = LocalDate.of(2026, 9, 27);

        assertThat(calculator.deadlineRisk(ProjectStatus.COMPLETED, null, today).normalizedScore())
                .isEqualTo(0.0);
        assertThat(calculator.deadlineRisk(ProjectStatus.CANCELLED, today.minusDays(2), today).normalizedScore())
                .isEqualTo(0.0);
        assertThat(calculator.deadlineRisk(ProjectStatus.IN_PROGRESS, null, today).available()).isFalse();
    }

    @Test
    void calculatesExpectedProgressAcrossProjectTimeline() {
        LocalDate start = LocalDate.of(2026, 9, 1);
        LocalDate end = LocalDate.of(2026, 9, 21);

        assertThat(calculator.expectedProgress(ProjectStatus.SANCTIONED, start, end, start.minusDays(1))
                .normalizedScore()).isEqualTo(0.0);
        assertThat(calculator.expectedProgress(ProjectStatus.IN_PROGRESS, start, end, start.plusDays(10))
                .normalizedScore()).isEqualTo(50.0);
        assertThat(calculator.expectedProgress(ProjectStatus.DELAYED, start, end, end.plusDays(1))
                .normalizedScore()).isEqualTo(100.0);
        assertThat(calculator.expectedProgress(ProjectStatus.COMPLETED, null, null, end)
                .normalizedScore()).isEqualTo(100.0);
    }

    @Test
    void handlesMissingInvalidAndSameDaySchedules() {
        LocalDate day = LocalDate.of(2026, 9, 27);

        assertThat(calculator.expectedProgress(ProjectStatus.IN_PROGRESS, null, day, day).available()).isFalse();
        assertThat(calculator.expectedProgress(ProjectStatus.IN_PROGRESS, day, null, day).available()).isFalse();
        assertThat(calculator.expectedProgress(ProjectStatus.IN_PROGRESS, day, day.minusDays(1), day)
                .available()).isFalse();
        assertThat(calculator.expectedProgress(ProjectStatus.IN_PROGRESS, day, day, day.minusDays(1))
                .normalizedScore()).isEqualTo(0.0);
        assertThat(calculator.expectedProgress(ProjectStatus.IN_PROGRESS, day, day, day)
                .normalizedScore()).isEqualTo(100.0);
    }

    @Test
    void handlesExactTimelineBoundariesAndCancelledProjects() {
        LocalDate start = LocalDate.of(2026, 9, 1);
        LocalDate end = LocalDate.of(2026, 9, 21);

        assertThat(calculator.expectedProgress(ProjectStatus.IN_PROGRESS, start, end, start)
                .normalizedScore()).isZero();
        assertThat(calculator.expectedProgress(ProjectStatus.IN_PROGRESS, start, end, end)
                .normalizedScore()).isEqualTo(100.0);
        assertThat(calculator.expectedProgress(ProjectStatus.CANCELLED, start, end, start.plusDays(10))
                .normalizedScore()).isEqualTo(50.0);
        assertThatThrownBy(() -> calculator.expectedProgress(ProjectStatus.IN_PROGRESS, start, end, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Calculation date");
    }

    @Test
    void calculatesOnlyPositiveProgressGapAndClampsActualProgress() {
        PriorityFactorCalculator.FactorResult expected = calculator.expectedProgress(
                ProjectStatus.IN_PROGRESS,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 11));

        assertThat(calculator.progressGap(expected, 20).normalizedScore()).isEqualTo(30.0);
        assertThat(calculator.progressGap(expected, 50).normalizedScore()).isEqualTo(0.0);
        assertThat(calculator.progressGap(expected, 80).normalizedScore()).isEqualTo(0.0);
        assertThat(calculator.progressGap(expected, -20).normalizedScore()).isEqualTo(50.0);
        assertThat(calculator.progressGap(expected, 150).normalizedScore()).isEqualTo(0.0);
    }

    @Test
    void reportsProgressGapUnavailableWhenInputsAreMissing() {
        PriorityFactorCalculator.FactorResult unavailable = calculator.expectedProgress(
                ProjectStatus.IN_PROGRESS, null, null, LocalDate.of(2026, 9, 27));

        assertThat(calculator.progressGap(unavailable, 20).available()).isFalse();
        assertThat(calculator.progressGap(
                calculator.expectedProgress(ProjectStatus.COMPLETED, null, null, LocalDate.of(2026, 9, 27)), null)
                .available()).isFalse();
    }
}
