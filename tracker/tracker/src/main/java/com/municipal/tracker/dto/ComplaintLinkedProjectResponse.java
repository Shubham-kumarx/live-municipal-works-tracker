package com.municipal.tracker.dto;

import com.municipal.tracker.model.MunicipalProject;
import com.municipal.tracker.model.ProjectStatus;

public record ComplaintLinkedProjectResponse(
        Long id,
        String projectName,
        ProjectStatus status,
        String locationAddress,
        Integer progressPercentage) {
    public static ComplaintLinkedProjectResponse from(MunicipalProject project) {
        return new ComplaintLinkedProjectResponse(project.getId(), project.getProjectName(),
                project.getStatus(), project.getLocationAddress(), project.getProgressPercentage());
    }
}
