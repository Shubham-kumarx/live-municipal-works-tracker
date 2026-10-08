package com.municipal.tracker;

import com.municipal.tracker.dto.ProjectDelayRiskResponse;
import com.municipal.tracker.model.DelayRisk;
import com.municipal.tracker.model.MunicipalProject;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectDelayRiskResponseTest {
    @Test
    void representsAvailableCalculationWithoutPersistenceTypes() {
        ProjectDelayRiskResponse response = new ProjectDelayRiskResponse(
                4L, true, DelayRisk.AT_RISK, 60.0, 40.0, 20.0, false,
                "Project is behind expected progress by 20 percentage points",
                LocalDateTime.of(2026, 9, 28, 6, 0));

        assertThat(response.delayRisk()).isEqualTo(DelayRisk.AT_RISK);
        assertThat(response.progressGap()).isEqualTo(20.0);
        assertThat(ProjectDelayRiskResponse.class.getRecordComponents())
                .noneMatch(component -> component.getType().equals(MunicipalProject.class));
    }

    @Test
    void representsUnavailableCalculationExplicitly() {
        ProjectDelayRiskResponse response = new ProjectDelayRiskResponse(
                5L, false, null, null, 20.0, null, false,
                "Expected end date is missing", LocalDateTime.of(2026, 9, 28, 6, 0));

        assertThat(response.available()).isFalse();
        assertThat(response.delayRisk()).isNull();
        assertThat(response.actualProgress()).isEqualTo(20.0);
        assertThat(response.progressGap()).isNull();
        assertThat(response.reason()).isEqualTo("Expected end date is missing");
    }
}
