package com.municipal.tracker.dto;

import com.municipal.tracker.model.MunicipalProject;
import com.municipal.tracker.model.ProjectStatus;
import com.municipal.tracker.model.ProjectType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ProjectResponse {
    private Long id;
    private String projectName;
    private String description;
    private ProjectType projectType;
    private ProjectStatus status;
    private Double latitude;
    private Double longitude;
    private String locationAddress;
    private Double budgetAllocated;
    private Double budgetSpent;
    private LocalDate startDate;
    private LocalDate expectedEndDate;
    private LocalDate actualEndDate;
    private Integer progressPercentage;
    private String progressNote;
    private Long wardId;
    private UserSummary assignedWorker;
    private UserSummary createdBy;
    private List<String> photoUrls;
    private Boolean flagged;
    private Integer flagCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastStatusUpdate;

    @Data
    @Builder
    public static class UserSummary {
        private Long id;
        private String fullName;
    }

    public static ProjectResponse from(MunicipalProject project) {
        return ProjectResponse.builder()
                .id(project.getId()).projectName(project.getProjectName()).description(project.getDescription())
                .projectType(project.getProjectType()).status(project.getStatus())
                .latitude(project.getLatitude()).longitude(project.getLongitude())
                .locationAddress(project.getLocationAddress()).budgetAllocated(project.getBudgetAllocated())
                .budgetSpent(project.getBudgetSpent()).startDate(project.getStartDate())
                .expectedEndDate(project.getExpectedEndDate()).actualEndDate(project.getActualEndDate())
                .progressPercentage(project.getProgressPercentage()).progressNote(project.getProgressNote())
                .wardId(project.getWard() == null ? null : project.getWard().getId())
                .assignedWorker(summary(project.getAssignedWorker())).createdBy(summary(project.getCreatedBy()))
                .photoUrls(project.getPhotoUrls() == null ? List.of() : List.copyOf(project.getPhotoUrls()))
                .flagged(project.getFlagged()).flagCount(project.getFlagCount())
                .createdAt(project.getCreatedAt()).updatedAt(project.getUpdatedAt())
                .lastStatusUpdate(project.getLastStatusUpdate()).build();
    }

    private static UserSummary summary(com.municipal.tracker.model.User user) {
        return user == null ? null : UserSummary.builder().id(user.getId()).fullName(user.getFullName()).build();
    }
}
