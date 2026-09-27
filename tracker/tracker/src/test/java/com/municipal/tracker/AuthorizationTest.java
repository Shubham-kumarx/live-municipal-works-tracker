package com.municipal.tracker;

import com.municipal.tracker.model.MunicipalProject;
import com.municipal.tracker.model.Role;
import com.municipal.tracker.model.User;
import com.municipal.tracker.model.Ward;
import com.municipal.tracker.service.ProjectAccessService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthorizationTest {
    private final ProjectAccessService access = new ProjectAccessService();

    @Test
    void officerCanManageOnlyProjectsInOwnWard() {
        User officer = user(10L, Role.WARD_OFFICER, ward(1L));

        assertThatCode(() -> access.requireOfficerWardAccess(officer, project(ward(1L), null)))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> access.requireOfficerWardAccess(officer, project(ward(2L), null)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void workerCanMutateOnlyAssignedProject() {
        User worker = user(20L, Role.FIELD_WORKER, ward(1L));

        assertThatCode(() -> access.requireMutationAccess(worker, project(ward(1L), worker)))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> access.requireMutationAccess(worker,
                project(ward(1L), user(21L, Role.FIELD_WORKER, ward(1L)))))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void nullActorCannotAccessWard() {
        assertThatThrownBy(() -> access.requireWardAccess(null, 1L))
                .isInstanceOf(AccessDeniedException.class);
    }

    private Ward ward(Long id) {
        Ward ward = new Ward();
        ward.setId(id);
        return ward;
    }

    private User user(Long id, Role role, Ward ward) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        user.setWard(ward);
        return user;
    }

    private MunicipalProject project(Ward ward, User worker) {
        MunicipalProject project = new MunicipalProject();
        project.setWard(ward);
        project.setAssignedWorker(worker);
        return project;
    }
}
