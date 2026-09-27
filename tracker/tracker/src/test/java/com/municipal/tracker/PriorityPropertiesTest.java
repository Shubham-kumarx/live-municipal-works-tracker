package com.municipal.tracker;

import com.municipal.tracker.config.PriorityProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PriorityPropertiesTest {
    @Test
    void defaultWeightsTotalOneAndConfigurationIsValid() {
        PriorityProperties properties = new PriorityProperties();

        properties.validate();

        assertThat(properties.getSeverityWeight()
                + properties.getComplaintVolumeWeight()
                + properties.getImpactWeight()
                + properties.getDeadlineRiskWeight()
                + properties.getProgressGapWeight()).isEqualTo(1.0);
    }

    @Test
    void rejectsWeightsThatDoNotTotalOne() {
        PriorityProperties properties = new PriorityProperties();
        properties.setSeverityWeight(0.20);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Priority factor weights must total 1.0");
    }

    @Test
    void rejectsInvalidOperationalLimits() {
        PriorityProperties properties = new PriorityProperties();
        properties.setComplaintSaturationCount(0);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Complaint saturation count must be positive");
    }
}
