package com.municipal.tracker.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.delay-risk")
public class DelayRiskProperties {
    private double onTrackMaxGap = 10.0;
    private double atRiskMaxGap = 25.0;

    @PostConstruct
    public void validate() {
        validatePercentage(onTrackMaxGap, "on-track-max-gap");
        validatePercentage(atRiskMaxGap, "at-risk-max-gap");
        if (onTrackMaxGap >= atRiskMaxGap) {
            throw new IllegalStateException(
                    "Delay risk on-track maximum gap must be less than the at-risk maximum gap");
        }
    }

    private void validatePercentage(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 100.0) {
            throw new IllegalStateException(name + " must be between 0 and 100");
        }
    }
}
