package com.municipal.tracker;

import com.municipal.tracker.controller.ProjectController;
import com.municipal.tracker.dto.ProjectDelayRiskResponse;
import com.municipal.tracker.model.*;
import com.municipal.tracker.service.DelayRiskService;
import com.municipal.tracker.service.PriorityCalculationService;
import com.municipal.tracker.service.ProjectAccessService;
import com.municipal.tracker.service.ProjectService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ProjectDelayRiskControllerTest {
    private final ProjectService projects = mock(ProjectService.class);
    private final PriorityCalculationService priorities = mock(PriorityCalculationService.class);
    private final ProjectAccessService access = mock(ProjectAccessService.class);
    private final DelayRiskService delayRisks = mock(DelayRiskService.class);
    private final ProjectController controller = new ProjectController(
            projects, priorities, access, delayRisks);

    @Test
    void returnsSafeDelayRiskResponseAfterWardAccessCheck() {
        Ward ward = new Ward();
        ward.setId(2L);
        MunicipalProject project = new MunicipalProject();
        project.setId(9L);
        project.setWard(ward);
        User citizen = new User();
        citizen.setRole(Role.CITIZEN);
        citizen.setWard(ward);
        DelayRiskService.Calculation calculation = new DelayRiskService.Calculation(
                project, true, DelayRisk.AT_RISK, 60.0, 40.0, 20.0,
                false, "Project is behind expected progress", LocalDateTime.of(2026, 9, 28, 6, 0));
        when(delayRisks.calculate(9L)).thenReturn(calculation);

        ProjectDelayRiskResponse response = controller.getDelayRisk(9L, citizen).getBody();

        verify(access).requireProjectWardAccess(citizen, project);
        assertThat(response).isNotNull();
        assertThat(response.projectId()).isEqualTo(9L);
        assertThat(response.delayRisk()).isEqualTo(DelayRisk.AT_RISK);
    }

    @Test
    void endpointExplicitlyRequiresAnApplicationRole() throws Exception {
        PreAuthorize authorization = ProjectController.class
                .getMethod("getDelayRisk", Long.class, User.class)
                .getAnnotation(PreAuthorize.class);

        assertThat(authorization).isNotNull();
        assertThat(authorization.value()).contains("CITIZEN", "MUNICIPAL_ADMIN", "AUDITOR");
    }

    @Test
    void existingAccessRuleRejectsAUserFromAnotherWard() {
        Ward projectWard = new Ward();
        projectWard.setId(2L);
        Ward userWard = new Ward();
        userWard.setId(3L);
        MunicipalProject project = new MunicipalProject();
        project.setWard(projectWard);
        User citizen = new User();
        citizen.setRole(Role.CITIZEN);
        citizen.setWard(userWard);

        assertThatThrownBy(() -> new ProjectAccessService().requireProjectWardAccess(citizen, project))
                .isInstanceOf(AccessDeniedException.class);
    }
}
