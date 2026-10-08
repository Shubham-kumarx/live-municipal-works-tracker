package com.municipal.tracker;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveLoggingConfigurationTest {

    @Test
    void productionDefaultsDoNotLogSensitiveRequestDetails() throws IOException {
        Properties properties = new Properties();
        try (InputStream source = getClass().getResourceAsStream("/application.properties")) {
            assertThat(source).as("application.properties must be available").isNotNull();
            properties.load(source);
        }

        assertThat(properties.getProperty("spring.mvc.log-request-details")).isEqualTo("false");
        assertThat(properties.getProperty("logging.level.org.springframework.web")).isEqualTo("INFO");
        assertThat(properties.getProperty("logging.level.org.springframework.security")).isEqualTo("INFO");
    }
}
