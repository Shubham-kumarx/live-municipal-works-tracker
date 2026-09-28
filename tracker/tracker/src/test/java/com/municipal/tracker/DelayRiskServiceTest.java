package com.municipal.tracker;

import com.municipal.tracker.config.DelayRiskProperties;
import com.municipal.tracker.config.PriorityProperties;
import com.municipal.tracker.model.DelayRisk;
import com.municipal.tracker.model.MunicipalProject;
import com.municipal.tracker.model.ProjectStatus;
import com.municipal.tracker.repository.ProjectRepository;
import com.municipal.tracker.service.DelayRiskService;
import com.municipal.tracker.service.PriorityFactorCalculator;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class DelayRiskServiceTest {
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final PriorityFactorCalculator factors = mock(PriorityFactorCalculator.class);
    private final DelayRiskService service = new DelayRiskService(
            projects, factors, new DelayRiskProperties());

    @Test
    void reusesExpectedProgressAndProgressGapCalculations() {
        LocalDate date = LocalDate.of(2026, 9, 28);
        MunicipalProject project = activeProject(1L, date.plusDays(10), 40);
        PriorityFactorCalculator.FactorResult expected = new PriorityFactorCalculator.FactorResult(
                true, "60.00", 60.0, "Expected progress is 60%");
        PriorityFactorCalculator.FactorResult gap = new PriorityFactorCalculator.FactorResult(
                true, "expected=60.00, actual=40.00", 20.0,
                "Project is behind expected progress by 20 percentage points");
        when(projects.findById(1L)).thenReturn(Optional.of(project));
        when(factors.expectedProgress(ProjectStatus.IN_PROGRESS, project.getStartDate(),
                project.getExpectedEndDate(), date)).thenReturn(expected);
        when(factors.progressGap(expected, 40)).thenReturn(gap);

        DelayRiskService.Calculation result = service.calculate(
                1L, date, LocalDateTime.of(2026, 9, 28, 6, 0));

        assertThat(result.delayRisk()).isEqualTo(DelayRisk.AT_RISK);
        assertThat(result.expectedProgress()).isEqualTo(60.0);
        assertThat(result.progressGap()).isEqualTo(20.0);
        verify(factors).expectedProgress(ProjectStatus.IN_PROGRESS, project.getStartDate(),
                project.getExpectedEndDate(), date);
        verify(factors).progressGap(expected, 40);
    }

    @Test
    void completedProjectIsOnTrackEvenWhenStoredProgressIsInconsistent() {
        LocalDate date = LocalDate.of(2026, 9, 28);
        MunicipalProject project = activeProject(2L, date.minusDays(10), 40);
        project.setStatus(ProjectStatus.COMPLETED);
        when(projects.findById(2L)).thenReturn(Optional.of(project));

        DelayRiskService.Calculation result = service.calculate(
                2L, date, LocalDateTime.of(2026, 9, 28, 6, 0));

        assertThat(result.available()).isTrue();
        assertThat(result.delayRisk()).isEqualTo(DelayRisk.ON_TRACK);
        assertThat(result.progressGap()).isEqualTo(0.0);
        assertThat(result.overdue()).isFalse();
        verifyNoInteractions(factors);
    }

    @Test
    void cancelledProjectReturnsExplicitUnavailableResult() {
        LocalDate date = LocalDate.of(2026, 9, 28);
        MunicipalProject project = activeProject(3L, date.minusDays(10), 40);
        project.setStatus(ProjectStatus.CANCELLED);
        when(projects.findById(3L)).thenReturn(Optional.of(project));

        DelayRiskService.Calculation result = service.calculate(
                3L, date, LocalDateTime.of(2026, 9, 28, 6, 0));

        assertThat(result.available()).isFalse();
        assertThat(result.delayRisk()).isNull();
        assertThat(result.reason()).contains("cancelled");
        verifyNoInteractions(factors);
    }

    @Test
    void missingAndInvalidDatesReturnControlledUnavailableResults() {
        LocalDate date = LocalDate.of(2026, 9, 28);
        DelayRiskService realService = realService();
        MunicipalProject missingStart = activeProject(4L, date.plusDays(10), 20);
        missingStart.setStartDate(null);
        MunicipalProject missingEnd = activeProject(5L, null, 20);
        MunicipalProject invalidRange = activeProject(6L, date.minusDays(10), 20);
        invalidRange.setStartDate(date);
        when(projects.findById(4L)).thenReturn(Optional.of(missingStart));
        when(projects.findById(5L)).thenReturn(Optional.of(missingEnd));
        when(projects.findById(6L)).thenReturn(Optional.of(invalidRange));

        assertThat(realService.calculate(4L, date, date.atStartOfDay()).available()).isFalse();
        assertThat(realService.calculate(5L, date, date.atStartOfDay()).reason())
                .contains("required");
        assertThat(realService.calculate(6L, date, date.atStartOfDay()).reason())
                .contains("before the start date");
    }

    @Test
    void missingActualProgressRemainsNullAndUnavailable() {
        LocalDate date = LocalDate.of(2026, 9, 28);
        MunicipalProject project = activeProject(7L, date.plusDays(10), null);
        when(projects.findById(7L)).thenReturn(Optional.of(project));

        DelayRiskService.Calculation result = realService().calculate(
                7L, date, LocalDateTime.of(2026, 9, 28, 6, 0));

        assertThat(result.available()).isFalse();
        assertThat(result.actualProgress()).isNull();
        assertThat(result.progressGap()).isNull();
        assertThat(result.reason()).contains("progress is missing");
    }

    @Test
    void futureProjectWithZeroProgressIsOnTrack() {
        LocalDate date = LocalDate.of(2026, 9, 28);
        MunicipalProject project = activeProject(8L, date.plusDays(40), 0);
        project.setStartDate(date.plusDays(10));
        when(projects.findById(8L)).thenReturn(Optional.of(project));

        DelayRiskService.Calculation result = realService().calculate(
                8L, date, LocalDateTime.of(2026, 9, 28, 6, 0));

        assertThat(result.available()).isTrue();
        assertThat(result.expectedProgress()).isEqualTo(0.0);
        assertThat(result.progressGap()).isEqualTo(0.0);
        assertThat(result.delayRisk()).isEqualTo(DelayRisk.ON_TRACK);
    }

    @Test
    void classifiesExactConfiguredGapBoundaries() {
        assertThat(service.classifyGap(10.0)).isEqualTo(DelayRisk.ON_TRACK);
        assertThat(service.classifyGap(11.0)).isEqualTo(DelayRisk.AT_RISK);
        assertThat(service.classifyGap(25.0)).isEqualTo(DelayRisk.AT_RISK);
        assertThat(service.classifyGap(26.0)).isEqualTo(DelayRisk.HIGH_DELAY_RISK);
    }

    @Test
    void overdueIncompleteProjectAlwaysHasHighDelayRisk() {
        LocalDate date = LocalDate.of(2026, 9, 28);
        MunicipalProject project = activeProject(9L, date.minusDays(1), 99);
        when(projects.findById(9L)).thenReturn(Optional.of(project));

        DelayRiskService.Calculation result = realService().calculate(
                9L, date, LocalDateTime.of(2026, 9, 28, 6, 0));

        assertThat(result.overdue()).isTrue();
        assertThat(result.delayRisk()).isEqualTo(DelayRisk.HIGH_DELAY_RISK);
        assertThat(result.reason()).contains("deadline has passed");
    }

    @Test
    void overdueProjectAtOneHundredPercentUsesGapRuleWithoutIncompleteOverride() {
        LocalDate date = LocalDate.of(2026, 9, 28);
        MunicipalProject project = activeProject(10L, date.minusDays(1), 100);
        when(projects.findById(10L)).thenReturn(Optional.of(project));

        DelayRiskService.Calculation result = realService().calculate(
                10L, date, LocalDateTime.of(2026, 9, 28, 6, 0));

        assertThat(result.overdue()).isTrue();
        assertThat(result.delayRisk()).isEqualTo(DelayRisk.ON_TRACK);
        assertThat(result.progressGap()).isEqualTo(0.0);
    }

    @Test
    void dueTodayIsNotPastDeadline() {
        LocalDate date = LocalDate.of(2026, 9, 28);
        MunicipalProject project = activeProject(11L, date, 100);
        when(projects.findById(11L)).thenReturn(Optional.of(project));

        DelayRiskService.Calculation result = realService().calculate(
                11L, date, LocalDateTime.of(2026, 9, 28, 6, 0));

        assertThat(result.overdue()).isFalse();
        assertThat(result.delayRisk()).isEqualTo(DelayRisk.ON_TRACK);
    }

    @Test
    void unknownProjectReturnsNotFound() {
        when(projects.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.calculate(99L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404 NOT_FOUND");
    }

    private DelayRiskService realService() {
        return new DelayRiskService(projects,
                new PriorityFactorCalculator(new PriorityProperties()), new DelayRiskProperties());
    }

    private MunicipalProject activeProject(Long id, LocalDate expectedEndDate, Integer progress) {
        MunicipalProject project = new MunicipalProject();
        project.setId(id);
        project.setStatus(ProjectStatus.IN_PROGRESS);
        project.setStartDate(LocalDate.of(2026, 9, 1));
        project.setExpectedEndDate(expectedEndDate);
        project.setProgressPercentage(progress);
        return project;
    }
}
