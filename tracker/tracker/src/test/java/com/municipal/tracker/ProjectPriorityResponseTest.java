package com.municipal.tracker;

import com.municipal.tracker.dto.ProjectPriorityResponse;
import com.municipal.tracker.model.MunicipalProject;
import com.municipal.tracker.model.ProjectPriorityLevel;
import com.municipal.tracker.service.PriorityCalculationService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectPriorityResponseTest {
    @Test
    void mapsCalculationWithoutPersistenceEntities() {
        MunicipalProject project = new MunicipalProject();
        project.setId(3L);
        project.setProjectName("Drain repair");
        PriorityCalculationService.FactorContribution factor =
                new PriorityCalculationService.FactorContribution(
                        "IMPACT", false, null, null, 0.20, 0.0,
                        "Project impact has not been classified");
        PriorityCalculationService.Calculation calculation = new PriorityCalculationService.Calculation(
                project, 12.5, ProjectPriorityLevel.LOW, List.of(factor),
                List.of("IMPACT: Project impact has not been classified"),
                LocalDateTime.of(2026, 9, 27, 12, 0));

        ProjectPriorityResponse response = ProjectPriorityResponse.from(calculation);

        assertThat(response.projectId()).isEqualTo(3L);
        assertThat(response.projectName()).isEqualTo("Drain repair");
        assertThat(response.totalScore()).isEqualTo(12.5);
        assertThat(response.priorityLevel()).isEqualTo(ProjectPriorityLevel.LOW);
        assertThat(response.advisoryNotice()).contains("not an official government formula");
        assertThat(response.factors().get(0).available()).isFalse();
        assertThat(response.factors().get(0).weight()).isEqualTo(0.20);
        assertThat(response.reasons()).containsExactly(
                "IMPACT: Project impact has not been classified");
        assertThat(ProjectPriorityResponse.class.getRecordComponents())
                .noneMatch(component -> component.getType().equals(MunicipalProject.class));
    }
}
