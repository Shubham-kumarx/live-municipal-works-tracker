package com.municipal.tracker;

import com.municipal.tracker.model.MunicipalProject;
import com.municipal.tracker.model.Role;
import com.municipal.tracker.model.User;
import com.municipal.tracker.model.Ward;
import com.municipal.tracker.service.ProjectAccessService;
import com.municipal.tracker.service.ProjectService;
import com.municipal.tracker.service.WardService;
import com.municipal.tracker.service.AuthService;
import com.municipal.tracker.service.LoginAttemptService;
import com.municipal.tracker.config.JwtUtil;
import com.municipal.tracker.dto.RegisterRequest;
import com.municipal.tracker.repository.UserRepository;
import com.municipal.tracker.repository.WardRepository;
import com.municipal.tracker.repository.ProjectRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import com.municipal.tracker.repository.ProjectFlagRepository;
import jakarta.persistence.EntityManager;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

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

    @Test
    void wardServiceChecksAccessBeforeLoadingWardForUpdate() {
        WardRepository wards = mock(WardRepository.class);
        WardService service = new WardService(wards, access);
        User officer = user(10L, Role.WARD_OFFICER, ward(1L));

        assertThatThrownBy(() -> service.updateWard(2L, new Ward(), officer))
                .isInstanceOf(AccessDeniedException.class);
        verify(wards, never()).findById(anyLong());
    }

    @Test
    void authServiceRejectsUnsupportedStaffRoleBeforePersistence() {
        UserRepository users = mock(UserRepository.class);
        AuthService service = new AuthService(users, mock(WardRepository.class),
                mock(PasswordEncoder.class), mock(JwtUtil.class), mock(AuthenticationManager.class),
                mock(LoginAttemptService.class));
        RegisterRequest request = new RegisterRequest();
        request.setRole(Role.MUNICIPAL_ADMIN);

        assertThatThrownBy(() -> service.registerStaff(
                request, user(1L, Role.MUNICIPAL_ADMIN, ward(1L))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("FIELD_WORKER or WARD_OFFICER");
        verifyNoInteractions(users);
    }

    @Test
    void nonCitizenCannotFlagProject() {
        ProjectRepository projects = mock(ProjectRepository.class);
        ProjectService service = new ProjectService(projects, mock(WardRepository.class),
                mock(UserRepository.class), mock(SimpMessagingTemplate.class), access,
                mock(ProjectFlagRepository.class), mock(EntityManager.class));

        assertThatThrownBy(() -> service.flagProject(1L,
                user(1L, Role.MUNICIPAL_ADMIN, ward(1L))))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(projects);
    }

    @Test
    void repeatedCitizenFlagDoesNotIncrementProjectAgain() {
        ProjectRepository projects = mock(ProjectRepository.class);
        ProjectFlagRepository flags = mock(ProjectFlagRepository.class);
        EntityManager entityManager = mock(EntityManager.class);
        MunicipalProject project = project(ward(1L), null);
        project.setId(7L);
        project.setFlagCount(3);
        User citizen = user(11L, Role.CITIZEN, ward(1L));
        when(projects.findById(7L)).thenReturn(java.util.Optional.of(project));
        when(flags.existsByProjectIdAndCitizenId(7L, 11L)).thenReturn(true);
        ProjectService service = new ProjectService(projects, mock(WardRepository.class),
                mock(UserRepository.class), mock(SimpMessagingTemplate.class), access,
                flags, entityManager);

        assertThatCode(() -> service.flagProject(7L, citizen)).doesNotThrowAnyException();
        org.assertj.core.api.Assertions.assertThat(project.getFlagCount()).isEqualTo(3);
        verify(entityManager).lock(project, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        verify(projects, never()).save(any());
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
