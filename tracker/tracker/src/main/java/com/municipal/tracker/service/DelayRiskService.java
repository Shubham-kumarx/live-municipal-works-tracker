package com.municipal.tracker.service;

import com.municipal.tracker.config.DelayRiskProperties;
import com.municipal.tracker.model.DelayRisk;
import com.municipal.tracker.model.MunicipalProject;
import com.municipal.tracker.model.ProjectStatus;
import com.municipal.tracker.model.User;
import com.municipal.tracker.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class DelayRiskService {
    private final ProjectRepository projectRepository;
    private final PriorityFactorCalculator factorCalculator;
    private final DelayRiskProperties properties;
    private final ProjectAccessService projectAccessService;

    @Transactional(readOnly = true)
    public Calculation calculateForActor(Long projectId, User actor) {
        Calculation calculation = calculate(projectId);
        projectAccessService.requireProjectWardAccess(actor, calculation.project());
        return calculation;
    }

    @Transactional(readOnly = true)
    public Calculation calculate(Long projectId) {
        return calculate(projectId, LocalDate.now(), LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public Calculation calculate(Long projectId, LocalDate calculationDate, LocalDateTime calculatedAt) {
        MunicipalProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Project not found: " + projectId));

        return calculate(project, calculationDate, calculatedAt);
    }

    public Calculation calculate(MunicipalProject project, LocalDate calculationDate,
                                 LocalDateTime calculatedAt) {

        Double actualProgress = project.getProgressPercentage() == null
                ? null : clamp(project.getProgressPercentage().doubleValue());
        if (project.getStatus() == ProjectStatus.COMPLETED) {
            return new Calculation(project, true, DelayRisk.ON_TRACK, 100.0, actualProgress,
                    0.0, false, "Completed project has no active delay risk", calculatedAt);
        }
        if (project.getStatus() == ProjectStatus.CANCELLED) {
            return new Calculation(project, false, null, null, actualProgress,
                    null, false, "Delay risk does not apply to a cancelled project", calculatedAt);
        }

        PriorityFactorCalculator.FactorResult expected = factorCalculator.expectedProgress(
                project.getStatus(), project.getStartDate(), project.getExpectedEndDate(), calculationDate);
        if (actualProgress == null) {
            return new Calculation(project, false, null, expected.normalizedScore(), null,
                    null, false, "Actual project progress is missing", calculatedAt);
        }
        PriorityFactorCalculator.FactorResult gap = factorCalculator.progressGap(
                expected, project.getProgressPercentage());

        if (!expected.available() || expected.normalizedScore() == null
                || !gap.available() || gap.normalizedScore() == null) {
            String reason = !expected.available() ? expected.explanation() : gap.explanation();
            return new Calculation(project, false, null, expected.normalizedScore(), actualProgress,
                    null, false, reason, calculatedAt);
        }

        boolean deadlinePassed = project.getExpectedEndDate() != null
                && project.getExpectedEndDate().isBefore(calculationDate);
        DelayRisk risk = deadlinePassed && actualProgress < 100.0
                ? DelayRisk.HIGH_DELAY_RISK
                : classifyGap(gap.normalizedScore());
        String reason = deadlinePassed && actualProgress < 100.0
                ? "Project deadline has passed while work remains incomplete"
                : gap.explanation();

        return new Calculation(project, true, risk, round(expected.normalizedScore()), actualProgress,
                round(gap.normalizedScore()), deadlinePassed, reason, calculatedAt);
    }

    public DelayRisk classifyGap(double progressGap) {
        double safeGap = clamp(progressGap);
        if (safeGap <= properties.getOnTrackMaxGap()) return DelayRisk.ON_TRACK;
        if (safeGap <= properties.getAtRiskMaxGap()) return DelayRisk.AT_RISK;
        return DelayRisk.HIGH_DELAY_RISK;
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(100.0, value));
    }

    private static double round(double value) {
        return Math.round(clamp(value) * 100.0) / 100.0;
    }

    public record Calculation(
            MunicipalProject project,
            boolean available,
            DelayRisk delayRisk,
            Double expectedProgress,
            Double actualProgress,
            Double progressGap,
            boolean overdue,
            String reason,
            LocalDateTime calculatedAt) {
    }
}
