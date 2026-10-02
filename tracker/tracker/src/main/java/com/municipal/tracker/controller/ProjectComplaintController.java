package com.municipal.tracker.controller;

import com.municipal.tracker.dto.LinkedComplaintResponse;
import com.municipal.tracker.model.User;
import com.municipal.tracker.service.ComplaintService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectComplaintController {
    private final ComplaintService complaintService;

    @GetMapping("/{projectId}/complaints")
    @PreAuthorize("hasAnyRole('CITIZEN','FIELD_WORKER','WARD_OFFICER','MUNICIPAL_ADMIN','AUDITOR')")
    public ResponseEntity<List<LinkedComplaintResponse>> getLinkedComplaints(
            @PathVariable Long projectId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(complaintService.getLinkedComplaints(projectId, currentUser));
    }
}
