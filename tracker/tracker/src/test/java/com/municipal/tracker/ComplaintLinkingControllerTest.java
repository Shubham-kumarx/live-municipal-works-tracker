package com.municipal.tracker;

import com.municipal.tracker.controller.ComplaintController;
import com.municipal.tracker.controller.ProjectComplaintController;
import com.municipal.tracker.dto.*;
import com.municipal.tracker.model.*;
import com.municipal.tracker.service.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ComplaintLinkingControllerTest {
    private final ComplaintService complaints = mock(ComplaintService.class);
    private final ComplaintController complaintController = new ComplaintController(
            mock(ImageAnalysisService.class), complaints, mock(ComplaintImageAccessService.class));

    @Test
    void delegatesLinkAndUnlinkToService() {
        User admin = new User(); admin.setRole(Role.MUNICIPAL_ADMIN);
        ComplaintResponse linked = mock(ComplaintResponse.class);
        when(complaints.linkToProject(1L, 2L, admin)).thenReturn(linked);
        when(complaints.unlinkFromProject(1L, admin)).thenReturn(linked);

        var linkResponse = complaintController.link(1L, 2L, admin);
        var unlinkResponse = complaintController.unlink(1L, admin);

        assertThat(linkResponse.getStatusCode().value()).isEqualTo(200);
        assertThat(linkResponse.getBody()).isSameAs(linked);
        assertThat(unlinkResponse.getStatusCode().value()).isEqualTo(200);
        assertThat(unlinkResponse.getBody()).isSameAs(linked);
    }

    @Test
    void returnsLinkableProjectOptionsAndLinkedProjectDto() {
        User admin = new User(); admin.setRole(Role.MUNICIPAL_ADMIN);
        ProjectLinkOptionResponse option = new ProjectLinkOptionResponse(
                2L, "Road repair", ProjectStatus.IN_PROGRESS, 4L, "Test Road");
        ComplaintLinkedProjectResponse linkedProject = new ComplaintLinkedProjectResponse(
                2L, "Road repair", ProjectStatus.IN_PROGRESS, "Test Road", 50);
        when(complaints.listLinkableProjects(admin)).thenReturn(List.of(option));
        when(complaints.getLinkedProject(1L, admin)).thenReturn(Optional.of(linkedProject));

        assertThat(complaintController.linkableProjects(admin).getBody()).containsExactly(option);
        assertThat(complaintController.getLinkedProject(1L, admin).getBody()).isEqualTo(linkedProject);
    }

    @Test
    void returnsNoContentForUnlinkedComplaint() {
        User admin = new User(); admin.setRole(Role.MUNICIPAL_ADMIN);
        when(complaints.getLinkedProject(1L, admin)).thenReturn(Optional.empty());

        assertThat(complaintController.getLinkedProject(1L, admin).getStatusCode().value()).isEqualTo(204);
    }

    @Test
    void projectQueryChecksWardAndReturnsReporterFreeSummaries() {
        ProjectComplaintController controller = new ProjectComplaintController(complaints);
        User user = new User(); user.setRole(Role.CITIZEN);
        LinkedComplaintResponse summary = new LinkedComplaintResponse(
                3L, "/image.png", "Issue", "Road", ComplaintIssueType.POTHOLE,
                ComplaintSeverity.HIGH, ComplaintStatus.SUBMITTED, null);
        when(complaints.getLinkedComplaints(2L, user)).thenReturn(List.of(summary));

        List<LinkedComplaintResponse> response = controller.getLinkedComplaints(2L, user).getBody();

        verify(complaints).getLinkedComplaints(2L, user);
        assertThat(response).containsExactly(summary);
        assertThat(LinkedComplaintResponse.class.getRecordComponents())
                .noneMatch(component -> component.getName().toLowerCase().contains("reporter"));
    }
}
