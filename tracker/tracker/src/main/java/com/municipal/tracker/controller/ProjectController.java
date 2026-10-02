package com.municipal.tracker.controller;

import com.municipal.tracker.model.MunicipalProject;
import com.municipal.tracker.dto.ProjectResponse;
import com.municipal.tracker.dto.ProjectStatusUpdateRequest;
import com.municipal.tracker.dto.ProjectBudgetUpdateRequest;
import com.municipal.tracker.dto.ProjectCreateRequest;
import com.municipal.tracker.dto.ProjectImpactUpdateRequest;
import com.municipal.tracker.dto.ProjectPriorityResponse;
import com.municipal.tracker.dto.ProjectDelayRiskResponse;
import com.municipal.tracker.dto.WardProjectStatsResponse;
import jakarta.validation.Valid;
import com.municipal.tracker.model.ProjectStatus;
import com.municipal.tracker.model.User;
import com.municipal.tracker.service.ProjectService;
import com.municipal.tracker.service.PriorityCalculationService;
import com.municipal.tracker.service.DelayRiskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final PriorityCalculationService priorityCalculationService;
    private final DelayRiskService delayRiskService;

    @Autowired
    public ProjectController(ProjectService projectService,
                             PriorityCalculationService priorityCalculationService,
                             DelayRiskService delayRiskService) {
        this.projectService = projectService;
        this.priorityCalculationService = priorityCalculationService;
        this.delayRiskService = delayRiskService;
    }

    // ── GET all projects in a ward (public - for map)
    // GET http://localhost:8080/api/projects/ward/2
    @GetMapping("/ward/{wardId}")
    public ResponseEntity<List<ProjectResponse>> getProjectsByWard(
            @PathVariable Long wardId) {
        return ResponseEntity.ok(
                projectService.getProjectsByWard(wardId).stream().map(ProjectResponse::from).toList()
        );
    }

    // ── GET single project by ID
    // GET http://localhost:8080/api/projects/1
    @GetMapping("/{id}")
    public ResponseEntity<ProjectResponse> getProjectById(
            @PathVariable Long id) {
        return projectService.getProjectById(id)
                .map(ProjectResponse::from).map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ── GET ward dashboard stats
    // GET http://localhost:8080/api/projects/ward/2/stats
    @GetMapping("/ward/{wardId}/stats")
    public ResponseEntity<WardProjectStatsResponse> getWardStats(
            @PathVariable Long wardId) {
        return ResponseEntity.ok(
                projectService.getWardStats(wardId)
        );
    }

    // ── GET flagged projects in a ward
    // GET http://localhost:8080/api/projects/ward/2/flagged
    @GetMapping("/ward/{wardId}/flagged")
    @PreAuthorize("hasAnyRole('WARD_OFFICER','MUNICIPAL_ADMIN','AUDITOR')")
    public ResponseEntity<List<ProjectResponse>> getFlaggedProjects(
            @PathVariable Long wardId,
            @AuthenticationPrincipal User currentUser) {
        projectService.requireWardAccess(currentUser, wardId);
        return ResponseEntity.ok(
                projectService.getFlaggedProjects(wardId).stream().map(ProjectResponse::from).toList()
        );
    }

    // ── GET projects assigned to logged-in field worker
    // GET http://localhost:8080/api/projects/my-projects
    @GetMapping("/my-projects")
    @PreAuthorize("hasRole('FIELD_WORKER')")
    public ResponseEntity<List<ProjectResponse>> getMyProjects(
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(
                projectService.getProjectsByWorker(currentUser.getId()).stream().map(ProjectResponse::from).toList()
        );
    }

    // ── CREATE a new project (Admin only)
    // POST http://localhost:8080/api/projects/ward/2
    @PostMapping("/ward/{wardId}")
    @PreAuthorize("hasAnyRole('MUNICIPAL_ADMIN','WARD_OFFICER')")
    public ResponseEntity<ProjectResponse> createProject(
            @PathVariable Long wardId,
            @Valid @RequestBody ProjectCreateRequest request,
            @AuthenticationPrincipal User currentUser) {
        MunicipalProject created = projectService.createProject(
                request.toEntity(), wardId, currentUser.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ProjectResponse.from(created));
    }

    // ── UPDATE project status (Field Worker or Officer)
    // PATCH http://localhost:8080/api/projects/1/status
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('FIELD_WORKER','WARD_OFFICER','MUNICIPAL_ADMIN')")
    public ResponseEntity<ProjectResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody ProjectStatusUpdateRequest body,
            @AuthenticationPrincipal User currentUser) {
        MunicipalProject updated = projectService.updateStatus(
                id, body.getStatus(), body.getProgressNote(), body.getProgressPercentage(), currentUser
        );
        return ResponseEntity.ok(ProjectResponse.from(updated));
    }

    // ── ASSIGN worker to project (Officer/Admin only)
    // PATCH http://localhost:8080/api/projects/1/assign/3
    @PatchMapping("/{projectId}/assign/{workerId}")
    @PreAuthorize("hasAnyRole('WARD_OFFICER','MUNICIPAL_ADMIN')")
    public ResponseEntity<ProjectResponse> assignWorker(
            @PathVariable Long projectId,
            @PathVariable Long workerId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(
                ProjectResponse.from(projectService.assignWorker(projectId, workerId, currentUser)));
    }

    // ── FLAG a project (any logged-in citizen)
    // PATCH http://localhost:8080/api/projects/1/flag
    @PatchMapping("/{id}/flag")
    public ResponseEntity<ProjectResponse> flagProject(
            @PathVariable Long id) {
        return ResponseEntity.ok(ProjectResponse.from(projectService.flagProject(id)));
    }

    // ── UPDATE budget spent (Officer/Admin only)
    // PATCH http://localhost:8080/api/projects/1/budget
    @PatchMapping("/{id}/budget")
    @PreAuthorize("hasAnyRole('WARD_OFFICER','MUNICIPAL_ADMIN')")
    public ResponseEntity<ProjectResponse> updateBudget(
            @PathVariable Long id,
            @Valid @RequestBody ProjectBudgetUpdateRequest body,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ProjectResponse.from(
                projectService.updateBudgetSpent(id, body.getAmountSpent(), currentUser)));
    }

    @PatchMapping("/{id}/impact")
    @PreAuthorize("hasAnyRole('WARD_OFFICER','MUNICIPAL_ADMIN')")
    public ResponseEntity<ProjectResponse> updateImpact(
            @PathVariable Long id,
            @Valid @RequestBody ProjectImpactUpdateRequest body,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ProjectResponse.from(
                projectService.updateImpactLevel(id, body.getImpactLevel(), currentUser)));
    }

    @GetMapping("/{id}/priority")
    @PreAuthorize("hasAnyRole('CITIZEN','FIELD_WORKER','WARD_OFFICER','MUNICIPAL_ADMIN','AUDITOR')")
    public ResponseEntity<ProjectPriorityResponse> getPriority(
            @PathVariable Long id,
            @AuthenticationPrincipal User currentUser) {
        PriorityCalculationService.Calculation calculation =
                priorityCalculationService.calculateForActor(id, currentUser);
        return ResponseEntity.ok(ProjectPriorityResponse.from(calculation));
    }

    @GetMapping("/{id}/delay-risk")
    @PreAuthorize("hasAnyRole('CITIZEN','FIELD_WORKER','WARD_OFFICER','MUNICIPAL_ADMIN','AUDITOR')")
    public ResponseEntity<ProjectDelayRiskResponse> getDelayRisk(
            @PathVariable Long id,
            @AuthenticationPrincipal User currentUser) {
        DelayRiskService.Calculation calculation = delayRiskService.calculateForActor(id, currentUser);
        return ResponseEntity.ok(ProjectDelayRiskResponse.from(calculation));
    }
}
