package com.municipal.tracker;

import com.municipal.tracker.model.*;
import com.municipal.tracker.repository.*;
import com.municipal.tracker.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ComplaintLinkingAuthorizationTest {
    private final ComplaintRepository complaints = mock(ComplaintRepository.class);
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final ProjectAccessService access = new ProjectAccessService();
    private final ComplaintService service = new ComplaintService(
            complaints, mock(ComplaintImageService.class), projects, access);

    @Test
    void citizenCannotManageAssociation() {
        Ward ward = ward(1L);
        Complaint complaint = complaint(1L, user(Role.CITIZEN, ward));
        MunicipalProject project = project(2L, ward);
        when(complaints.findById(1L)).thenReturn(Optional.of(complaint));
        when(projects.findById(2L)).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> service.linkToProject(1L, 2L, user(Role.CITIZEN, ward)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void wardOfficerCannotLinkAcrossWards() {
        Complaint complaint = complaint(1L, user(Role.CITIZEN, ward(2L)));
        MunicipalProject project = project(2L, ward(1L));
        when(complaints.findById(1L)).thenReturn(Optional.of(complaint));
        when(projects.findById(2L)).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> service.linkToProject(1L, 2L, user(Role.WARD_OFFICER, ward(1L))))
                .isInstanceOf(AccessDeniedException.class);
    }

    private Ward ward(Long id) { Ward ward = new Ward(); ward.setId(id); return ward; }
    private User user(Role role, Ward ward) { User user = new User(); user.setRole(role); user.setWard(ward); return user; }
    private Complaint complaint(Long id, User reporter) { Complaint c = new Complaint(); c.setId(id); c.setReportingUser(reporter); return c; }
    private MunicipalProject project(Long id, Ward ward) { MunicipalProject p = new MunicipalProject(); p.setId(id); p.setWard(ward); return p; }
}
