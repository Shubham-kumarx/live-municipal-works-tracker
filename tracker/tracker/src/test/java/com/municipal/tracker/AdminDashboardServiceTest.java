package com.municipal.tracker;

import com.municipal.tracker.dto.AdminDashboardResponse;
import com.municipal.tracker.model.Complaint;
import com.municipal.tracker.model.ComplaintIssueType;
import com.municipal.tracker.model.ComplaintPredictionState;
import com.municipal.tracker.model.ComplaintSeverity;
import com.municipal.tracker.model.ComplaintStatus;
import com.municipal.tracker.model.DelayRisk;
import com.municipal.tracker.model.MunicipalProject;
import com.municipal.tracker.model.ProjectStatus;
import com.municipal.tracker.model.ProjectPriorityLevel;
import com.municipal.tracker.model.Role;
import com.municipal.tracker.model.User;
import com.municipal.tracker.model.Ward;
import com.municipal.tracker.repository.ComplaintRepository;
import com.municipal.tracker.repository.ProjectRepository;
import com.municipal.tracker.service.AdminDashboardService;
import com.municipal.tracker.service.DelayRiskService;
import com.municipal.tracker.service.PriorityCalculationService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;

class AdminDashboardServiceTest {
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final ComplaintRepository complaints = mock(ComplaintRepository.class);
    private final PriorityCalculationService priorities = mock(PriorityCalculationService.class);
    private final DelayRiskService delayRisks = mock(DelayRiskService.class);
    private final AdminDashboardService service = new AdminDashboardService(
            projects, complaints, priorities, delayRisks);

    @Test
    void municipalAdminReceivesGlobalWorkMetrics() {
        User admin = user(Role.MUNICIPAL_ADMIN, null);
        List<MunicipalProject> scopedProjects = List.of(
                project(1L, ProjectStatus.IN_PROGRESS),
                project(2L, ProjectStatus.COMPLETED),
                project(3L, ProjectStatus.DELAYED),
                project(4L, ProjectStatus.SANCTIONED));
        scopedProjects.forEach(this::stubDecision);
        when(projects.findAllByOrderByCreatedAtDesc()).thenReturn(scopedProjects);
        when(complaints.findByMunicipalProjectIdIn(List.of(1L, 2L, 3L, 4L))).thenReturn(List.of());

        AdminDashboardResponse response = service.getDashboard(admin);

        assertThat(response.workMetrics().total()).isEqualTo(4);
        assertThat(response.workMetrics().active()).isEqualTo(1);
        assertThat(response.workMetrics().completed()).isEqualTo(1);
        assertThat(response.workMetrics().delayed()).isEqualTo(1);
        verify(projects).findAllByOrderByCreatedAtDesc();
    }

    @Test
    void wardOfficerReceivesOnlyAssignedWardWorkMetrics() {
        Ward ward = new Ward();
        ward.setId(7L);
        User officer = user(Role.WARD_OFFICER, ward);
        MunicipalProject scopedProject = project(1L, ProjectStatus.IN_PROGRESS);
        stubDecision(scopedProject);
        when(projects.findByWardIdOrderByCreatedAtDesc(7L)).thenReturn(List.of(scopedProject));
        when(complaints.findByMunicipalProjectIdIn(List.of(1L))).thenReturn(List.of());

        AdminDashboardResponse response = service.getDashboard(officer);

        assertThat(response.workMetrics().total()).isEqualTo(1);
        verify(projects).findByWardIdOrderByCreatedAtDesc(7L);
    }

    @Test
    void calculatesPriorityAndDelayRiskForEveryScopedProject() {
        User admin = user(Role.MUNICIPAL_ADMIN, null);
        MunicipalProject project = project(10L, ProjectStatus.IN_PROGRESS);
        Complaint complaint = new Complaint();
        complaint.setMunicipalProject(project);
        when(projects.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(project));
        when(complaints.findByMunicipalProjectIdIn(List.of(10L))).thenReturn(List.of(complaint));
        when(priorities.calculate(eq(project), anyList(), any(LocalDate.class), any(LocalDateTime.class)))
                .thenReturn(new PriorityCalculationService.Calculation(project, 80,
                        ProjectPriorityLevel.CRITICAL, List.of(), List.of(), LocalDateTime.now()));
        when(delayRisks.calculate(eq(project), any(LocalDate.class), any(LocalDateTime.class)))
                .thenReturn(new DelayRiskService.Calculation(project, true,
                        DelayRisk.HIGH_DELAY_RISK, 80.0, 20.0, 60.0, false,
                        "Project is behind schedule", LocalDateTime.now()));

        AdminDashboardResponse response = service.getDashboard(admin);

        assertThat(response.workMetrics().highPriority()).isEqualTo(1);
        assertThat(response.workMetrics().highDelayRisk()).isEqualTo(1);
        assertThat(response.works()).singleElement().satisfies(row -> {
            assertThat(row.priorityScore()).isEqualTo(80);
            assertThat(row.delayRisk()).isEqualTo(DelayRisk.HIGH_DELAY_RISK);
            assertThat(row.linkedComplaintCount()).isEqualTo(1);
        });
    }

    @Test
    void countsAndReturnsNewestRealComplaintsWithoutReporterDetails() {
        User admin = user(Role.MUNICIPAL_ADMIN, null);
        Complaint assisted = complaint(2L, ComplaintSeverity.HIGH, ComplaintIssueType.POTHOLE);
        Complaint manual = complaint(1L, ComplaintSeverity.LOW, null);
        when(projects.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());
        when(complaints.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(assisted, manual));

        AdminDashboardResponse response = service.getDashboard(admin);

        assertThat(response.complaintMetrics().total()).isEqualTo(2);
        assertThat(response.complaintMetrics().unresolved()).isEqualTo(2);
        assertThat(response.complaintMetrics().aiAssisted()).isEqualTo(1);
        assertThat(response.recentComplaints()).extracting(AdminDashboardResponse.RecentComplaint::id)
                .containsExactly(2L, 1L);
        assertThat(response.recentComplaints().get(0).aiAssisted()).isTrue();
    }

    @Test
    void wardOfficerComplaintMetricsUseReporterWardScope() {
        Ward ward = new Ward();
        ward.setId(8L);
        User officer = user(Role.WARD_OFFICER, ward);
        when(projects.findByWardIdOrderByCreatedAtDesc(8L)).thenReturn(List.of());
        when(complaints.findByReportingUserWardIdOrderByCreatedAtDesc(8L))
                .thenReturn(List.of(complaint(3L, ComplaintSeverity.MEDIUM, null)));

        AdminDashboardResponse response = service.getDashboard(officer);

        assertThat(response.complaintMetrics().total()).isEqualTo(1);
        verify(complaints).findByReportingUserWardIdOrderByCreatedAtDesc(8L);
    }

    @Test
    void includesZeroSafeStatusAndSeverityDistributions() {
        User admin = user(Role.MUNICIPAL_ADMIN, null);
        MunicipalProject active = project(20L, ProjectStatus.IN_PROGRESS);
        stubDecision(active);
        when(projects.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(active));
        when(complaints.findByMunicipalProjectIdIn(List.of(20L))).thenReturn(List.of());
        when(complaints.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of(complaint(4L, ComplaintSeverity.CRITICAL, null)));

        AdminDashboardResponse response = service.getDashboard(admin);

        assertThat(response.workStatusDistribution())
                .contains(new AdminDashboardResponse.DistributionItem("IN_PROGRESS", 1),
                        new AdminDashboardResponse.DistributionItem("COMPLETED", 0));
        assertThat(response.complaintSeverityDistribution())
                .contains(new AdminDashboardResponse.DistributionItem("CRITICAL", 1),
                        new AdminDashboardResponse.DistributionItem("LOW", 0));
    }

    private MunicipalProject project(Long id, ProjectStatus status) {
        MunicipalProject project = new MunicipalProject();
        project.setId(id);
        project.setStatus(status);
        return project;
    }

    private void stubDecision(MunicipalProject project) {
        when(priorities.calculate(eq(project), anyList(), any(LocalDate.class), any(LocalDateTime.class)))
                .thenReturn(new PriorityCalculationService.Calculation(project, 10,
                        ProjectPriorityLevel.LOW, List.of(), List.of(), LocalDateTime.now()));
        when(delayRisks.calculate(eq(project), any(LocalDate.class), any(LocalDateTime.class)))
                .thenReturn(new DelayRiskService.Calculation(project, true,
                        DelayRisk.ON_TRACK, 10.0, 0.0, 10.0, false,
                        "On track", LocalDateTime.now()));
    }

    private Complaint complaint(Long id, ComplaintSeverity severity, ComplaintIssueType aiIssueType) {
        Complaint complaint = new Complaint();
        complaint.setId(id);
        complaint.setDescription("Municipal issue " + id);
        complaint.setLocationAddress("Ward road");
        complaint.setFinalIssueType(ComplaintIssueType.POTHOLE);
        complaint.setFinalSeverity(severity);
        complaint.setStatus(ComplaintStatus.SUBMITTED);
        complaint.setPredictionState(aiIssueType == null
                ? ComplaintPredictionState.MANUAL : ComplaintPredictionState.CONFIRMED);
        complaint.setAiPredictedIssueType(aiIssueType);
        complaint.setCreatedAt(LocalDateTime.of(2026, 9, 28, 6, id.intValue()));
        return complaint;
    }

    private User user(Role role, Ward ward) {
        User user = new User();
        user.setRole(role);
        user.setWard(ward);
        return user;
    }
}
