package com.municipal.tracker.controller;

import com.municipal.tracker.dto.AIAnalysisResponse;
import com.municipal.tracker.dto.ComplaintCreateRequest;
import com.municipal.tracker.dto.ComplaintResponse;
import com.municipal.tracker.dto.ComplaintLinkedProjectResponse;
import com.municipal.tracker.dto.ProjectLinkOptionResponse;
import com.municipal.tracker.model.User;
import com.municipal.tracker.service.ComplaintService;
import com.municipal.tracker.service.ImageAnalysisService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/complaints")
@RequiredArgsConstructor
public class ComplaintController {
    private final ImageAnalysisService imageAnalysisService;
    private final ComplaintService complaintService;

    @PostMapping("/analyze")
    @PreAuthorize("hasRole('CITIZEN')")
    public ResponseEntity<AIAnalysisResponse> analyze(@RequestParam("image") MultipartFile image) {
        return ResponseEntity.ok(imageAnalysisService.analyze(image));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('CITIZEN')")
    public ResponseEntity<ComplaintResponse> create(
            @Valid @RequestPart("complaint") ComplaintCreateRequest request,
            @RequestPart("image") MultipartFile image,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(complaintService.create(request, image, currentUser));
    }

    @GetMapping("/{complaintId}/project")
    @PreAuthorize("hasAnyRole('WARD_OFFICER','MUNICIPAL_ADMIN')")
    public ResponseEntity<ComplaintLinkedProjectResponse> getLinkedProject(
            @PathVariable Long complaintId,
            @AuthenticationPrincipal User currentUser) {
        return complaintService.getLinkedProject(complaintId, currentUser)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('WARD_OFFICER','MUNICIPAL_ADMIN')")
    public ResponseEntity<List<ComplaintResponse>> list(
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(complaintService.listForManager(currentUser));
    }

    @GetMapping("/linkable-projects")
    @PreAuthorize("hasAnyRole('WARD_OFFICER','MUNICIPAL_ADMIN')")
    public ResponseEntity<List<ProjectLinkOptionResponse>> linkableProjects(
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(complaintService.listLinkableProjects(currentUser));
    }

    @PatchMapping("/{complaintId}/project/{projectId}")
    @PreAuthorize("hasAnyRole('WARD_OFFICER','MUNICIPAL_ADMIN')")
    public ResponseEntity<ComplaintResponse> link(
            @PathVariable Long complaintId,
            @PathVariable Long projectId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(complaintService.linkToProject(complaintId, projectId, currentUser));
    }

    @DeleteMapping("/{complaintId}/project")
    @PreAuthorize("hasAnyRole('WARD_OFFICER','MUNICIPAL_ADMIN')")
    public ResponseEntity<ComplaintResponse> unlink(
            @PathVariable Long complaintId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(complaintService.unlinkFromProject(complaintId, currentUser));
    }
}
