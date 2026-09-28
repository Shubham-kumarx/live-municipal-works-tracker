package com.municipal.tracker;

import com.municipal.tracker.config.PriorityProperties;
import com.municipal.tracker.model.*;
import com.municipal.tracker.repository.ComplaintRepository;
import com.municipal.tracker.repository.ProjectRepository;
import com.municipal.tracker.service.PriorityCalculationService;
import com.municipal.tracker.service.PriorityFactorCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PriorityCalculationServiceTest {
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final ComplaintRepository complaints = mock(ComplaintRepository.class);
    private PriorityCalculationService service;

    @BeforeEach
    void setUp() {
        PriorityProperties properties = new PriorityProperties();
        service = new PriorityCalculationService(projects, complaints,
                new PriorityFactorCalculator(properties), properties);
    }

    @Test
    void calculatesWeightedScoreAndHumanReadableReasons() {
        LocalDate date = LocalDate.of(2026, 9, 27);
        MunicipalProject project = project(7L);
        project.setImpactLevel(ProjectImpactLevel.HIGH);
        project.setStartDate(date.minusDays(10));
        project.setExpectedEndDate(date);
        project.setProgressPercentage(40);
        Complaint complaint = new Complaint();
        complaint.setFinalSeverity(ComplaintSeverity.CRITICAL);
        when(projects.findById(7L)).thenReturn(Optional.of(project));
        when(complaints.findByMunicipalProjectId(7L)).thenReturn(List.of(complaint));

        PriorityCalculationService.Calculation result = service.calculate(
                7L, date, LocalDateTime.of(2026, 9, 27, 12, 0));

        assertThat(result.totalScore()).isEqualTo(73.0);
        assertThat(result.priorityLevel()).isEqualTo(ProjectPriorityLevel.HIGH);
        assertThat(result.factors()).extracting(PriorityCalculationService.FactorContribution::weightedContribution)
                .containsExactly(30.0, 4.0, 15.0, 15.0, 9.0);
        assertThat(result.reasons()).hasSize(5);
    }

    @Test
    void missingOptionalDataStaysVisibleAndDoesNotRedistributeWeights() {
        MunicipalProject project = project(8L);
        when(projects.findById(8L)).thenReturn(Optional.of(project));
        when(complaints.findByMunicipalProjectId(8L)).thenReturn(List.of());

        PriorityCalculationService.Calculation result = service.calculate(
                8L, LocalDate.of(2026, 9, 27), LocalDateTime.of(2026, 9, 27, 12, 0));

        assertThat(result.totalScore()).isEqualTo(0.0);
        assertThat(result.priorityLevel()).isEqualTo(ProjectPriorityLevel.LOW);
        assertThat(result.factors()).filteredOn(factor -> !factor.available()).hasSize(4);
    }

    @Test
    void calculatesFromAnAlreadyLoadedProjectAndComplaints() {
        LocalDate date = LocalDate.of(2026, 9, 27);
        MunicipalProject project = project(9L);
        project.setImpactLevel(ProjectImpactLevel.MEDIUM);
        Complaint complaint = new Complaint();
        complaint.setFinalSeverity(ComplaintSeverity.HIGH);

        PriorityCalculationService.Calculation result = service.calculate(
                project, List.of(complaint), date, LocalDateTime.of(2026, 9, 27, 12, 0));

        assertThat(result.project()).isSameAs(project);
        assertThat(result.factors()).hasSize(5);
        assertThat(result.totalScore()).isBetween(0.0, 100.0);
    }

    @Test
    void unknownProjectReturnsNotFound() {
        when(projects.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.calculate(99L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404 NOT_FOUND");
    }

    @Test
    void classifiesEveryConfiguredThresholdBoundary() {
        assertThat(service.classifyScore(0.0)).isEqualTo(ProjectPriorityLevel.LOW);
        assertThat(service.classifyScore(29.99)).isEqualTo(ProjectPriorityLevel.LOW);
        assertThat(service.classifyScore(30.0)).isEqualTo(ProjectPriorityLevel.MEDIUM);
        assertThat(service.classifyScore(54.99)).isEqualTo(ProjectPriorityLevel.MEDIUM);
        assertThat(service.classifyScore(55.0)).isEqualTo(ProjectPriorityLevel.HIGH);
        assertThat(service.classifyScore(74.99)).isEqualTo(ProjectPriorityLevel.HIGH);
        assertThat(service.classifyScore(75.0)).isEqualTo(ProjectPriorityLevel.CRITICAL);
        assertThat(service.classifyScore(100.0)).isEqualTo(ProjectPriorityLevel.CRITICAL);
    }

    private MunicipalProject project(Long id) {
        MunicipalProject project = new MunicipalProject();
        project.setId(id);
        project.setStatus(ProjectStatus.IN_PROGRESS);
        project.setStartDate(LocalDate.of(2026, 9, 1));
        project.setProgressPercentage(0);
        return project;
    }
}
