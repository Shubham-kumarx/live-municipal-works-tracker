package com.municipal.tracker.dto;

import com.municipal.tracker.model.MunicipalProject;
import com.municipal.tracker.model.ProjectType;
import com.municipal.tracker.model.ProjectImpactLevel;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDate;

@Data
public class ProjectCreateRequest {
    @NotBlank(message = "Project name is required") @Size(max = 255)
    private String projectName;
    @Size(max = 10000) private String description;
    @NotNull(message = "Project type is required") private ProjectType projectType;
    @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") private Double latitude;
    @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") private Double longitude;
    @NotBlank(message = "Location address is required") @Size(max = 500)
    private String locationAddress;
    @NotNull @DecimalMin(value = "0.0", message = "Allocated budget cannot be negative")
    private Double budgetAllocated;
    @NotNull(message = "Start date is required") private LocalDate startDate;
    private LocalDate expectedEndDate;
    private ProjectImpactLevel impactLevel;

    public MunicipalProject toEntity() {
        MunicipalProject project = new MunicipalProject();
        project.setProjectName(projectName); project.setDescription(description); project.setProjectType(projectType);
        project.setLatitude(latitude); project.setLongitude(longitude); project.setLocationAddress(locationAddress);
        project.setBudgetAllocated(budgetAllocated); project.setStartDate(startDate);
        project.setExpectedEndDate(expectedEndDate);
        project.setImpactLevel(impactLevel);
        return project;
    }
}
