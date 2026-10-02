package com.municipal.tracker;

import com.municipal.tracker.controller.ProjectController;
import com.municipal.tracker.dto.ProjectPriorityResponse;
import com.municipal.tracker.model.*;
import com.municipal.tracker.service.PriorityCalculationService;
import com.municipal.tracker.service.ProjectAccessService;
import com.municipal.tracker.service.ProjectService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ProjectPriorityControllerTest {
    private final ProjectService projects = mock(ProjectService.class);
    private final PriorityCalculationService priorities = mock(PriorityCalculationService.class);
    private final ProjectAccessService access = mock(ProjectAccessService.class);
    private final ProjectController controller = new ProjectController(projects, priorities, access);

    @Test
    void returnsSafePriorityResponseAfterWardAccessCheck() {
        Ward ward = new Ward();
        ward.setId(2L);
        MunicipalProject project = new MunicipalProject();
        project.setId(9L);
        project.setProjectName("Road repair");
        project.setWard(ward);
        User citizen = new User();
        citizen.setId(4L);
        citizen.setRole(Role.CITIZEN);
        citizen.setWard(ward);
        PriorityCalculationService.Calculation calculation = new PriorityCalculationService.Calculation(
                project, 40.0, ProjectPriorityLevel.MEDIUM, List.of(), List.of(),
                LocalDateTime.of(2026, 9, 27, 12, 0));
        when(priorities.calculate(9L)).thenReturn(calculation);

        ProjectPriorityResponse response = controller.getPriority(9L, citizen).getBody();

        verify(access).requireProjectWardAccess(citizen, project);
        assertThat(response).isNotNull();
        assertThat(response.projectId()).isEqualTo(9L);
        assertThat(response.priorityLevel()).isEqualTo(ProjectPriorityLevel.MEDIUM);
    }

    @Test
    void endpointExplicitlyRequiresAnApplicationRole() throws Exception {
        PreAuthorize authorization = ProjectController.class
                .getMethod("getPriority", Long.class, User.class)
                .getAnnotation(PreAuthorize.class);

        assertThat(authorization).isNotNull();
        assertThat(authorization.value()).contains("CITIZEN", "MUNICIPAL_ADMIN", "AUDITOR");
    }

    @Test
    void realAccessServiceRejectsCitizenFromAnotherWard() {
        Ward projectWard = new Ward();
        projectWard.setId(2L);
        Ward citizenWard = new Ward();
        citizenWard.setId(3L);
        MunicipalProject project = new MunicipalProject();
        project.setWard(projectWard);
        User citizen = new User();
        citizen.setRole(Role.CITIZEN);
        citizen.setWard(citizenWard);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> new ProjectAccessService().requireProjectWardAccess(citizen, project))
                .isInstanceOf(AccessDeniedException.class);
    }
}
