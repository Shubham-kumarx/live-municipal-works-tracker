package com.municipal.tracker.service;

import com.municipal.tracker.config.PriorityProperties;
import com.municipal.tracker.model.Complaint;
import com.municipal.tracker.model.MunicipalProject;
import com.municipal.tracker.model.ProjectPriorityLevel;
import com.municipal.tracker.repository.ComplaintRepository;
import com.municipal.tracker.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PriorityCalculationService {
    private final ProjectRepository projectRepository;
    private final ComplaintRepository complaintRepository;
    private final PriorityFactorCalculator factorCalculator;
    private final PriorityProperties properties;

    public Calculation calculate(Long projectId) {
        return calculate(projectId, LocalDate.now(), LocalDateTime.now());
    }

    public Calculation calculate(Long projectId, LocalDate calculationDate, LocalDateTime calculatedAt) {
        MunicipalProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Project not found: " + projectId));
        List<Complaint> complaints = complaintRepository.findByMunicipalProjectId(projectId);

        PriorityFactorCalculator.FactorResult severity = factorCalculator.severity(
                complaints.stream().map(Complaint::getFinalSeverity).toList());
        PriorityFactorCalculator.FactorResult volume = factorCalculator.complaintVolume(complaints.size());
        PriorityFactorCalculator.FactorResult impact = factorCalculator.impact(project.getImpactLevel());
        PriorityFactorCalculator.FactorResult deadline = factorCalculator.deadlineRisk(
                project.getStatus(), project.getExpectedEndDate(), calculationDate);
        PriorityFactorCalculator.FactorResult expected = factorCalculator.expectedProgress(
                project.getStatus(), project.getStartDate(), project.getExpectedEndDate(), calculationDate);
        PriorityFactorCalculator.FactorResult progressGap = factorCalculator.progressGap(
                expected, project.getProgressPercentage());

        List<FactorContribution> factors = List.of(
                contribution("SEVERITY", severity, properties.getSeverityWeight()),
                contribution("COMPLAINT_VOLUME", volume, properties.getComplaintVolumeWeight()),
                contribution("IMPACT", impact, properties.getImpactWeight()),
                contribution("DEADLINE_RISK", deadline, properties.getDeadlineRiskWeight()),
                contribution("PROGRESS_GAP", progressGap, properties.getProgressGapWeight())
        );
        double total = round(factors.stream().mapToDouble(FactorContribution::weightedContribution).sum());
        List<String> reasons = factors.stream()
                .map(factor -> factor.name() + ": " + factor.explanation())
                .toList();

        return new Calculation(project, total, classifyScore(total), factors, reasons, calculatedAt);
    }

    private FactorContribution contribution(String name, PriorityFactorCalculator.FactorResult result,
                                            double weight) {
        double contribution = result.available() && result.normalizedScore() != null
                ? result.normalizedScore() * weight : 0.0;
        return new FactorContribution(name, result.available(), result.rawValue(), result.normalizedScore(),
                weight, round(contribution), result.explanation());
    }

    public ProjectPriorityLevel classifyScore(double score) {
        if (score >= properties.getCriticalPriorityThreshold()) return ProjectPriorityLevel.CRITICAL;
        if (score >= properties.getHighPriorityThreshold()) return ProjectPriorityLevel.HIGH;
        if (score >= properties.getMediumPriorityThreshold()) return ProjectPriorityLevel.MEDIUM;
        return ProjectPriorityLevel.LOW;
    }

    private static double round(double value) {
        return Math.round(Math.max(0.0, Math.min(100.0, value)) * 100.0) / 100.0;
    }

    public record FactorContribution(
            String name,
            boolean available,
            String rawValue,
            Double normalizedScore,
            double weight,
            double weightedContribution,
            String explanation) {
    }

    public record Calculation(
            MunicipalProject project,
            double totalScore,
            ProjectPriorityLevel priorityLevel,
            List<FactorContribution> factors,
            List<String> reasons,
            LocalDateTime calculatedAt) {
    }
}
