package com.municipal.tracker;

import com.municipal.tracker.model.Complaint;
import com.municipal.tracker.model.Role;
import com.municipal.tracker.model.User;
import com.municipal.tracker.model.Ward;
import com.municipal.tracker.repository.ComplaintRepository;
import com.municipal.tracker.service.ComplaintImageAccessService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ComplaintImageAuthorizationTest {
    @Test
    void unrelatedCitizenCannotReadComplaintImage() {
        ComplaintRepository complaints = mock(ComplaintRepository.class);
        User reporter = user(1L, Role.CITIZEN, 4L);
        Complaint complaint = new Complaint();
        complaint.setId(10L);
        complaint.setReportingUser(reporter);
        complaint.setImageUrl("/uploads/complaints/test.png");
        when(complaints.findById(10L)).thenReturn(Optional.of(complaint));
        ComplaintImageAccessService service = new ComplaintImageAccessService(
                complaints, Path.of("target", "security-test-uploads").toString());

        assertThatThrownBy(() -> service.load(10L, user(2L, Role.CITIZEN, 4L)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void officerFromAnotherWardCannotReadComplaintImage() {
        ComplaintRepository complaints = mock(ComplaintRepository.class);
        Complaint complaint = new Complaint();
        complaint.setId(10L);
        complaint.setReportingUser(user(1L, Role.CITIZEN, 4L));
        complaint.setImageUrl("/uploads/complaints/test.png");
        when(complaints.findById(10L)).thenReturn(Optional.of(complaint));
        ComplaintImageAccessService service = new ComplaintImageAccessService(
                complaints, Path.of("target", "security-test-uploads").toString());

        assertThatThrownBy(() -> service.load(10L, user(3L, Role.WARD_OFFICER, 5L)))
                .isInstanceOf(AccessDeniedException.class);
    }

    private User user(Long id, Role role, Long wardId) {
        Ward ward = new Ward(); ward.setId(wardId);
        User user = new User(); user.setId(id); user.setRole(role); user.setWard(ward); user.setActive(true);
        return user;
    }
}
