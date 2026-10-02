package com.municipal.tracker;

import com.municipal.tracker.config.DelayRiskProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DelayRiskPropertiesTest {
    @Test
    void defaultsMatchDocumentedThresholds() {
        DelayRiskProperties properties = new DelayRiskProperties();

        properties.validate();

        assertThat(properties.getOnTrackMaxGap()).isEqualTo(10.0);
        assertThat(properties.getAtRiskMaxGap()).isEqualTo(25.0);
    }

    @Test
    void rejectsNonIncreasingThresholds() {
        DelayRiskProperties properties = new DelayRiskProperties();
        properties.setOnTrackMaxGap(25.0);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be less than");
    }

    @Test
    void rejectsThresholdOutsidePercentageRange() {
        DelayRiskProperties properties = new DelayRiskProperties();
        properties.setAtRiskMaxGap(Double.NaN);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("between 0 and 100");
    }
}
