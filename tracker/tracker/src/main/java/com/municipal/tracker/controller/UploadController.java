package com.municipal.tracker.controller;

import com.municipal.tracker.model.MunicipalProject;
import com.municipal.tracker.model.User;
import com.municipal.tracker.dto.ProjectResponse;
import com.municipal.tracker.service.ProjectPhotoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
public class UploadController {

    private final ProjectPhotoService projectPhotoService;

    // Upload photos for a project
    // POST /api/upload/project/{projectId}/photos
    @PostMapping("/project/{projectId}/photos")
    @PreAuthorize("hasAnyRole('FIELD_WORKER','WARD_OFFICER','MUNICIPAL_ADMIN')")
    public ResponseEntity<ProjectResponse> uploadPhotos(
            @PathVariable Long projectId,
            @RequestParam("files") MultipartFile[] files,
            @AuthenticationPrincipal User currentUser) {

        MunicipalProject saved = projectPhotoService.upload(projectId, files, currentUser);
        return ResponseEntity.ok(ProjectResponse.from(saved));
    }
}
