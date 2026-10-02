package com.municipal.tracker;

import com.municipal.tracker.dto.ProjectCreateRequest;
import com.municipal.tracker.dto.ProjectImpactUpdateRequest;
import com.municipal.tracker.model.ProjectImpactLevel;
import com.municipal.tracker.config.PriorityProperties;
import com.municipal.tracker.service.PriorityFactorCalculator;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectImpactTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void copiesOptionalImpactFromCreateRequestToEntity() {
        ProjectCreateRequest request = new ProjectCreateRequest();
        request.setImpactLevel(ProjectImpactLevel.CRITICAL);

        assertThat(request.toEntity().getImpactLevel()).isEqualTo(ProjectImpactLevel.CRITICAL);
    }

    @Test
    void requiresImpactForFocusedUpdate() {
        ProjectImpactUpdateRequest request = new ProjectImpactUpdateRequest();

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("impactLevel");
    }

    @Test
    void normalizesOnlyExplicitImpactValues() {
        PriorityFactorCalculator calculator = new PriorityFactorCalculator(new PriorityProperties());

        assertThat(calculator.impact(ProjectImpactLevel.LOW).normalizedScore()).isEqualTo(25.0);
        assertThat(calculator.impact(ProjectImpactLevel.CRITICAL).normalizedScore()).isEqualTo(100.0);
        assertThat(calculator.impact(null).available()).isFalse();
    }
}
