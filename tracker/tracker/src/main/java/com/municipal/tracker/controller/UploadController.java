package com.municipal.tracker.controller;

import com.municipal.tracker.model.MunicipalProject;
import com.municipal.tracker.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class UploadController {

    private final ProjectRepository projectRepository;

    // Upload photos for a project
    // POST /api/upload/project/{projectId}/photos
    @PostMapping("/project/{projectId}/photos")
    public ResponseEntity<MunicipalProject> uploadPhotos(
            @PathVariable Long projectId,
            @RequestParam("files") MultipartFile[] files) {

        MunicipalProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found"));

        List<String> photoUrls = new ArrayList<>();
        if (project.getPhotoUrls() != null) {
            photoUrls.addAll(project.getPhotoUrls());
        }

        // Save files to local uploads folder
        Path uploadDir = Paths.get("uploads");
        try {
            Files.createDirectories(uploadDir);
            for (MultipartFile file : files) {
                if (file.isEmpty()) continue;
                String filename = UUID.randomUUID() + "_" + file.getOriginalFilename();
                Path filePath = uploadDir.resolve(filename);
                Files.copy(file.getInputStream(), filePath,
                        StandardCopyOption.REPLACE_EXISTING);
                // Store the URL path
                photoUrls.add("/uploads/" + filename);
            }
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }

        project.setPhotoUrls(photoUrls);
        MunicipalProject saved = projectRepository.save(project);
        return ResponseEntity.ok(saved);
    }
}
