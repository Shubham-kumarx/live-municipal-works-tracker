package com.municipal.tracker.dto;

import com.municipal.tracker.model.ProjectStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProjectStatusUpdateRequest {
    @NotNull(message = "Status is required")
    private ProjectStatus status;

    @Size(max = 5000, message = "Progress note must be at most 5000 characters")
    private String progressNote;

    @Min(value = 0, message = "Progress must be between 0 and 100")
    @Max(value = 100, message = "Progress must be between 0 and 100")
    private Integer progressPercentage;
}
