package com.municipal.tracker.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ProjectBudgetUpdateRequest {
    @NotNull(message = "Amount spent is required")
    @DecimalMin(value = "0.0", message = "Amount spent cannot be negative")
    private Double amountSpent;
}
