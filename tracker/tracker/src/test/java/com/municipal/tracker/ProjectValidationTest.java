package com.municipal.tracker;

import com.municipal.tracker.dto.ProjectStatusUpdateRequest;
import com.municipal.tracker.dto.ProjectCreateRequest;
import com.municipal.tracker.model.ProjectType;
import com.municipal.tracker.model.ProjectStatus;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectValidationTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void progressAcceptsInclusiveBoundaries() {
        assertThat(violationsForProgress(0)).isZero();
        assertThat(violationsForProgress(100)).isZero();
    }

    @Test
    void progressRejectsValuesOutsideInclusiveBoundaries() {
        assertThat(violationsForProgress(-1)).isOne();
        assertThat(violationsForProgress(101)).isOne();
    }

    @Test
    void projectDatesAllowMissingSameDayAndChronologicalDeadlines() {
        assertThat(dateViolations(null)).isZero();
        assertThat(dateViolations(LocalDate.of(2026, 10, 2))).isZero();
        assertThat(dateViolations(LocalDate.of(2026, 10, 3))).isZero();
    }

    @Test
    void projectDatesRejectDeadlineBeforeStart() {
        assertThat(dateViolations(LocalDate.of(2026, 10, 1))).isOne();
    }

    private int violationsForProgress(int progress) {
        ProjectStatusUpdateRequest request = new ProjectStatusUpdateRequest();
        request.setStatus(ProjectStatus.IN_PROGRESS);
        request.setProgressPercentage(progress);
        return validator.validate(request).size();
    }

    private long dateViolations(LocalDate expectedEndDate) {
        ProjectCreateRequest request = new ProjectCreateRequest();
        request.setProjectName("Road repair");
        request.setProjectType(ProjectType.ROAD_REPAIR);
        request.setLatitude(28.6);
        request.setLongitude(77.2);
        request.setLocationAddress("Test Road");
        request.setBudgetAllocated(1000.0);
        request.setStartDate(LocalDate.of(2026, 10, 2));
        request.setExpectedEndDate(expectedEndDate);
        return validator.validate(request).stream()
                .filter(violation -> violation.getPropertyPath().toString().equals("dateRangeValid"))
                .count();
    }
}
