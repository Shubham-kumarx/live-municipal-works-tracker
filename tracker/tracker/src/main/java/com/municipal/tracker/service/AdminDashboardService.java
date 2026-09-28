package com.municipal.tracker.service;

import com.municipal.tracker.dto.AdminDashboardResponse;
import com.municipal.tracker.model.Complaint;
import com.municipal.tracker.model.ComplaintSeverity;
import com.municipal.tracker.model.DelayRisk;
import com.municipal.tracker.model.MunicipalProject;
import com.municipal.tracker.model.ProjectPriorityLevel;
import com.municipal.tracker.model.ProjectStatus;
import com.municipal.tracker.model.Role;
import com.municipal.tracker.model.User;
import com.municipal.tracker.repository.ComplaintRepository;
import com.municipal.tracker.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Arrays;

@Service
@RequiredArgsConstructor
public class AdminDashboardService {
    private final ProjectRepository projectRepository;
    private final ComplaintRepository complaintRepository;
    private final PriorityCalculationService priorityCalculationService;
    private final DelayRiskService delayRiskService;

    @Transactional(readOnly = true)
    public AdminDashboardResponse getDashboard(User actor) {
        List<MunicipalProject> projects = projectsFor(actor);
        List<Complaint> scopedComplaints = complaintsFor(actor);
        LocalDate calculationDate = LocalDate.now();
        LocalDateTime generatedAt = LocalDateTime.now();
        List<Complaint> linkedComplaints = projects.isEmpty() ? List.of()
                : complaintRepository.findByMunicipalProjectIdIn(
                        projects.stream().map(MunicipalProject::getId).toList());
        Map<Long, List<Complaint>> complaintsByProject = linkedComplaints.stream()
                .collect(Collectors.groupingBy(complaint -> complaint.getMunicipalProject().getId()));
        List<AdminDashboardResponse.WorkDecisionRow> workRows = projects.stream()
                .map(project -> toWorkRow(project,
                        complaintsByProject.getOrDefault(project.getId(), List.of()),
                        calculationDate, generatedAt))
                .toList();

        AdminDashboardResponse.WorkMetrics workMetrics = new AdminDashboardResponse.WorkMetrics(
                projects.size(),
                countStatus(projects, ProjectStatus.IN_PROGRESS),
                countStatus(projects, ProjectStatus.COMPLETED),
                countStatus(projects, ProjectStatus.DELAYED),
                workRows.stream().filter(this::isHighPriority).count(),
                workRows.stream().filter(row -> row.delayRisk() == DelayRisk.HIGH_DELAY_RISK).count());
        AdminDashboardResponse.ComplaintMetrics complaintMetrics =
                new AdminDashboardResponse.ComplaintMetrics(
                        scopedComplaints.size(),
                        scopedComplaints.stream().filter(this::isUnresolved).count(),
                        scopedComplaints.stream().filter(this::isAiAssisted).count());
        List<AdminDashboardResponse.RecentComplaint> recentComplaints = scopedComplaints.stream()
                .limit(10)
                .map(this::toRecentComplaint)
                .toList();

        return new AdminDashboardResponse(
                workMetrics,
                complaintMetrics,
                workRows,
                recentComplaints,
                workStatusDistribution(projects),
                complaintSeverityDistribution(scopedComplaints),
                generatedAt);
    }

    private List<AdminDashboardResponse.DistributionItem> workStatusDistribution(
            List<MunicipalProject> projects) {
        return Arrays.stream(ProjectStatus.values())
                .map(status -> new AdminDashboardResponse.DistributionItem(
                        status.name(), countStatus(projects, status)))
                .toList();
    }

    private List<AdminDashboardResponse.DistributionItem> complaintSeverityDistribution(
            List<Complaint> complaints) {
        return Arrays.stream(ComplaintSeverity.values())
                .map(severity -> new AdminDashboardResponse.DistributionItem(
                        severity.name(), complaints.stream()
                        .filter(complaint -> complaint.getFinalSeverity() == severity).count()))
                .toList();
    }

    private List<Complaint> complaintsFor(User actor) {
        if (actor.getRole() == Role.MUNICIPAL_ADMIN) {
            return complaintRepository.findAllByOrderByCreatedAtDesc();
        }
        return complaintRepository.findByReportingUserWardIdOrderByCreatedAtDesc(actor.getWard().getId());
    }

    private boolean isUnresolved(Complaint complaint) {
        return complaint.getStatus() == null || !"RESOLVED".equals(complaint.getStatus().name());
    }

    private boolean isAiAssisted(Complaint complaint) {
        return complaint.getAiPredictedIssueType() != null;
    }

    private AdminDashboardResponse.RecentComplaint toRecentComplaint(Complaint complaint) {
        MunicipalProject project = complaint.getMunicipalProject();
        return new AdminDashboardResponse.RecentComplaint(
                complaint.getId(),
                complaint.getDescription(),
                complaint.getLocationAddress(),
                complaint.getFinalIssueType(),
                complaint.getFinalSeverity(),
                complaint.getStatus(),
                complaint.getPredictionState(),
                isAiAssisted(complaint),
                project == null ? null : project.getId(),
                project == null ? null : project.getProjectName(),
                complaint.getCreatedAt());
    }

    private AdminDashboardResponse.WorkDecisionRow toWorkRow(
            MunicipalProject project, List<Complaint> complaints, LocalDate calculationDate,
            LocalDateTime generatedAt) {
        PriorityCalculationService.Calculation priority = priorityCalculationService.calculate(
                project, complaints, calculationDate, generatedAt);
        DelayRiskService.Calculation delayRisk = delayRiskService.calculate(
                project, calculationDate, generatedAt);

        return new AdminDashboardResponse.WorkDecisionRow(
                project.getId(),
                project.getProjectName(),
                project.getProjectType(),
                project.getStatus(),
                project.getLocationAddress(),
                project.getWard() == null ? null : project.getWard().getId(),
                project.getWard() == null ? null : project.getWard().getWardName(),
                project.getAssignedWorker() == null ? null : project.getAssignedWorker().getFullName(),
                project.getExpectedEndDate(),
                project.getProgressPercentage(),
                priority.priorityLevel(),
                priority.totalScore(),
                delayRisk.available(),
                delayRisk.delayRisk(),
                delayRisk.expectedProgress(),
                delayRisk.progressGap(),
                delayRisk.reason(),
                complaints.size());
    }

    private boolean isHighPriority(AdminDashboardResponse.WorkDecisionRow row) {
        return row.priorityLevel() == ProjectPriorityLevel.HIGH
                || row.priorityLevel() == ProjectPriorityLevel.CRITICAL;
    }

    private List<MunicipalProject> projectsFor(User actor) {
        requireDashboardRole(actor);
        if (actor.getRole() == Role.MUNICIPAL_ADMIN) {
            return projectRepository.findAllByOrderByCreatedAtDesc();
        }
        if (actor.getWard() == null || actor.getWard().getId() == null) {
            throw new AccessDeniedException("Ward officer must be assigned to a ward");
        }
        return projectRepository.findByWardIdOrderByCreatedAtDesc(actor.getWard().getId());
    }

    private void requireDashboardRole(User actor) {
        if (actor == null || (actor.getRole() != Role.MUNICIPAL_ADMIN
                && actor.getRole() != Role.WARD_OFFICER)) {
            throw new AccessDeniedException("Administrative dashboard access is required");
        }
    }

    private long countStatus(List<MunicipalProject> projects, ProjectStatus status) {
        return projects.stream().filter(project -> project.getStatus() == status).count();
    }
}
