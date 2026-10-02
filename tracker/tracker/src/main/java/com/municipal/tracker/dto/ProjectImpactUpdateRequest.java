package com.municipal.tracker.dto;

import com.municipal.tracker.model.ProjectImpactLevel;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ProjectImpactUpdateRequest {
    @NotNull(message = "Impact level is required")
    private ProjectImpactLevel impactLevel;
}
