package com.municipal.tracker;

import com.municipal.tracker.dto.ComplaintCreateRequest;
import com.municipal.tracker.dto.ComplaintResponse;
import com.municipal.tracker.model.*;
import com.municipal.tracker.repository.ComplaintRepository;
import com.municipal.tracker.repository.ProjectRepository;
import com.municipal.tracker.service.ComplaintImageService;
import com.municipal.tracker.service.ComplaintService;
import com.municipal.tracker.service.ProjectAccessService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ComplaintServiceTest {
    private final ComplaintRepository repository = mock(ComplaintRepository.class);
    private final ComplaintImageService images = mock(ComplaintImageService.class);
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final ProjectAccessService access = mock(ProjectAccessService.class);
    private final ComplaintService service = new ComplaintService(repository, images, projects, access);

    @Test
    void createsManualComplaintWithoutAiMetadata() {
        User citizen = citizen();
        when(images.store(any())).thenReturn(new ComplaintImageService.StoredComplaintImage(
                Path.of("uploads/complaints/test.png"), "/uploads/complaints/test.png"));
        when(repository.save(any())).thenAnswer(invocation -> {
            Complaint complaint = invocation.getArgument(0); complaint.setId(12L); return complaint;
        });
        ComplaintCreateRequest request = new ComplaintCreateRequest("Blocked drain", "Test Road",
                null, null, null, null, null, null, ComplaintIssueType.WATERLOGGING,
                ComplaintSeverity.HIGH, ComplaintPredictionState.MANUAL);

        ComplaintResponse response = service.create(request,
                new MockMultipartFile("image", "issue.png", "image/png", new byte[]{1}), citizen);

        assertThat(response.id()).isEqualTo(12L);
        assertThat(response.reportingUserName()).isEqualTo("Citizen");
        assertThat(response.aiPredictedIssueType()).isNull();
    }

    @Test
    void rejectsInconsistentConfirmedPredictionBeforeWritingImage() {
        ComplaintCreateRequest request = new ComplaintCreateRequest("Issue", "Road", null, null,
                ComplaintIssueType.POTHOLE, 0.9, com.municipal.tracker.dto.AIAnalysisResponse.ConfidenceLevel.HIGH,
                ComplaintSeverity.HIGH, ComplaintIssueType.ROAD_CRACK, ComplaintSeverity.HIGH,
                ComplaintPredictionState.CONFIRMED);

        assertThatThrownBy(() -> service.create(request, mock(MockMultipartFile.class), citizen()))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(images, repository);
    }

    @Test
    void linksUnlinkedComplaintToExistingProject() {
        Complaint complaint = new Complaint();
        complaint.setId(10L); complaint.setReportingUser(citizen());
        MunicipalProject project = new MunicipalProject();
        project.setId(20L);
        when(repository.findById(10L)).thenReturn(java.util.Optional.of(complaint));
        when(projects.findById(20L)).thenReturn(java.util.Optional.of(project));
        when(repository.save(complaint)).thenReturn(complaint);
        User admin = new User(); admin.setRole(Role.MUNICIPAL_ADMIN);

        ComplaintResponse response = service.linkToProject(10L, 20L, admin);

        assertThat(response.municipalProjectId()).isEqualTo(20L);
        verify(access).requireOfficerWardAccess(admin, project);
    }

    @Test
    void unlinksComplaintWithoutDeletingEitherRecord() {
        Complaint complaint = new Complaint();
        complaint.setId(11L); complaint.setReportingUser(citizen());
        MunicipalProject project = new MunicipalProject(); project.setId(21L);
        complaint.setMunicipalProject(project);
        when(repository.findById(11L)).thenReturn(java.util.Optional.of(complaint));
        when(repository.save(complaint)).thenReturn(complaint);
        User admin = new User(); admin.setRole(Role.MUNICIPAL_ADMIN);

        ComplaintResponse response = service.unlinkFromProject(11L, admin);

        assertThat(response.municipalProjectId()).isNull();
        verify(repository).save(complaint);
        verify(repository, never()).delete(any());
        verify(projects, never()).delete(any());
    }

    @Test
    void rejectsDuplicateLink() {
        Complaint complaint = new Complaint(); complaint.setId(12L); complaint.setReportingUser(citizen());
        MunicipalProject project = new MunicipalProject(); project.setId(22L);
        complaint.setMunicipalProject(project);
        when(repository.findById(12L)).thenReturn(java.util.Optional.of(complaint));
        when(projects.findById(22L)).thenReturn(java.util.Optional.of(project));
        User admin = new User(); admin.setRole(Role.MUNICIPAL_ADMIN);

        assertThatThrownBy(() -> service.linkToProject(12L, 22L, admin))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("409 CONFLICT");
        verify(repository, never()).save(complaint);
    }

    @Test
    void rejectsMissingComplaintAndMissingProject() {
        User admin = new User(); admin.setRole(Role.MUNICIPAL_ADMIN);
        when(repository.findById(404L)).thenReturn(java.util.Optional.empty());
        Complaint complaint = new Complaint(); complaint.setId(13L); complaint.setReportingUser(citizen());
        when(repository.findById(13L)).thenReturn(java.util.Optional.of(complaint));
        when(projects.findById(404L)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.linkToProject(404L, 1L, admin))
                .hasMessageContaining("Complaint not found");
        assertThatThrownBy(() -> service.linkToProject(13L, 404L, admin))
                .hasMessageContaining("Project not found");
    }

    @Test
    void rejectsUnlinkWhenNoAssociationExists() {
        Complaint complaint = new Complaint(); complaint.setId(14L); complaint.setReportingUser(citizen());
        when(repository.findById(14L)).thenReturn(java.util.Optional.of(complaint));
        User admin = new User(); admin.setRole(Role.MUNICIPAL_ADMIN);

        assertThatThrownBy(() -> service.unlinkFromProject(14L, admin))
                .hasMessageContaining("409 CONFLICT");
    }

    private User citizen() {
        User user = new User(); user.setId(5L); user.setFullName("Citizen"); user.setRole(Role.CITIZEN);
        return user;
    }
}
