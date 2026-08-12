package com.municipal.tracker.controller;

import com.municipal.tracker.model.MunicipalProject;
import com.municipal.tracker.model.ProjectStatus;
import com.municipal.tracker.model.User;
import com.municipal.tracker.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ProjectController {

    private final ProjectService projectService;

    // ── GET all projects in a ward (public - for map)
    // GET http://localhost:8080/api/projects/ward/2
    @GetMapping("/ward/{wardId}")
    public ResponseEntity<List<MunicipalProject>> getProjectsByWard(
            @PathVariable Long wardId) {
        return ResponseEntity.ok(
                projectService.getProjectsByWard(wardId)
        );
    }

    // ── GET single project by ID
    // GET http://localhost:8080/api/projects/1
    @GetMapping("/{id}")
    public ResponseEntity<MunicipalProject> getProjectById(
            @PathVariable Long id) {
        return projectService.getProjectById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ── GET ward dashboard stats
    // GET http://localhost:8080/api/projects/ward/2/stats
    @GetMapping("/ward/{wardId}/stats")
    public ResponseEntity<Map<String, Object>> getWardStats(
            @PathVariable Long wardId) {
        return ResponseEntity.ok(
                projectService.getWardStats(wardId)
        );
    }

    // ── GET flagged projects in a ward
    // GET http://localhost:8080/api/projects/ward/2/flagged
    @GetMapping("/ward/{wardId}/flagged")
    @PreAuthorize("hasAnyRole('WARD_OFFICER','MUNICIPAL_ADMIN','AUDITOR')")
    public ResponseEntity<List<MunicipalProject>> getFlaggedProjects(
            @PathVariable Long wardId) {
        return ResponseEntity.ok(
                projectService.getFlaggedProjects(wardId)
        );
    }

    // ── GET projects assigned to logged-in field worker
    // GET http://localhost:8080/api/projects/my-projects
    @GetMapping("/my-projects")
    @PreAuthorize("hasRole('FIELD_WORKER')")
    public ResponseEntity<List<MunicipalProject>> getMyProjects(
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(
                projectService.getProjectsByWorker(currentUser.getId())
        );
    }

    // ── CREATE a new project (Admin only)
    // POST http://localhost:8080/api/projects/ward/2
    @PostMapping("/ward/{wardId}")
    @PreAuthorize("hasAnyRole('MUNICIPAL_ADMIN','WARD_OFFICER')")
    public ResponseEntity<MunicipalProject> createProject(
            @PathVariable Long wardId,
            @RequestBody MunicipalProject project,
            @AuthenticationPrincipal User currentUser) {
        try {
            MunicipalProject created = projectService.createProject(
                    project, wardId, currentUser.getId()
            );
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(created);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // ── UPDATE project status (Field Worker or Officer)
    // PATCH http://localhost:8080/api/projects/1/status
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('FIELD_WORKER','WARD_OFFICER','MUNICIPAL_ADMIN')")
    public ResponseEntity<MunicipalProject> updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        try {
            ProjectStatus status = ProjectStatus.valueOf(
                    (String) body.get("status")
            );
            String note = (String) body.get("progressNote");
            Integer progress = body.get("progressPercentage") != null
                    ? ((Number) body.get("progressPercentage")).intValue()
                    : null;

            MunicipalProject updated = projectService.updateStatus(
                    id, status, note, progress
            );
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // ── ASSIGN worker to project (Officer/Admin only)
    // PATCH http://localhost:8080/api/projects/1/assign/3
    @PatchMapping("/{projectId}/assign/{workerId}")
    @PreAuthorize("hasAnyRole('WARD_OFFICER','MUNICIPAL_ADMIN')")
    public ResponseEntity<MunicipalProject> assignWorker(
            @PathVariable Long projectId,
            @PathVariable Long workerId) {
        try {
            return ResponseEntity.ok(
                    projectService.assignWorker(projectId, workerId)
            );
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // ── FLAG a project (any logged-in citizen)
    // PATCH http://localhost:8080/api/projects/1/flag
    @PatchMapping("/{id}/flag")
    public ResponseEntity<MunicipalProject> flagProject(
            @PathVariable Long id) {
        try {
            return ResponseEntity.ok(
                    projectService.flagProject(id)
            );
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ── UPDATE budget spent (Officer/Admin only)
    // PATCH http://localhost:8080/api/projects/1/budget
    @PatchMapping("/{id}/budget")
    @PreAuthorize("hasAnyRole('WARD_OFFICER','MUNICIPAL_ADMIN')")
    public ResponseEntity<MunicipalProject> updateBudget(
            @PathVariable Long id,
            @RequestBody Map<String, Double> body) {
        try {
            return ResponseEntity.ok(
                    projectService.updateBudgetSpent(
                            id, body.get("amountSpent")
                    )
            );
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
