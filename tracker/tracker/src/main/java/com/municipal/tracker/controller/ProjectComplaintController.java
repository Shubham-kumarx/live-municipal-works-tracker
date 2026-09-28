package com.municipal.tracker.controller;

import com.municipal.tracker.dto.LinkedComplaintResponse;
import com.municipal.tracker.model.MunicipalProject;
import com.municipal.tracker.model.User;
import com.municipal.tracker.repository.ProjectRepository;
import com.municipal.tracker.service.ComplaintService;
import com.municipal.tracker.service.ProjectAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectComplaintController {
    private final ComplaintService complaintService;
    private final ProjectRepository projectRepository;
    private final ProjectAccessService projectAccessService;

    @GetMapping("/{projectId}/complaints")
    @PreAuthorize("hasAnyRole('CITIZEN','FIELD_WORKER','WARD_OFFICER','MUNICIPAL_ADMIN','AUDITOR')")
    public ResponseEntity<List<LinkedComplaintResponse>> getLinkedComplaints(
            @PathVariable Long projectId,
            @AuthenticationPrincipal User currentUser) {
        MunicipalProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Project not found: " + projectId));
        projectAccessService.requireProjectWardAccess(currentUser, project);
        return ResponseEntity.ok(complaintService.getLinkedComplaints(projectId));
    }
}
