package com.municipal.tracker.dto;

import com.municipal.tracker.model.MunicipalProject;
import com.municipal.tracker.model.ProjectStatus;

public record ProjectLinkOptionResponse(
        Long id,
        String projectName,
        ProjectStatus status,
        Long wardId,
        String locationAddress) {
    public static ProjectLinkOptionResponse from(MunicipalProject project) {
        return new ProjectLinkOptionResponse(project.getId(), project.getProjectName(), project.getStatus(),
                project.getWard() == null ? null : project.getWard().getId(), project.getLocationAddress());
    }
}
