package com.municipal.tracker.service;

import com.municipal.tracker.model.MunicipalProject;
import com.municipal.tracker.model.User;
import com.municipal.tracker.repository.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

import static org.springframework.http.HttpStatus.*;

@Service
public class ProjectPhotoService {
    private static final Logger log = LoggerFactory.getLogger(ProjectPhotoService.class);

    private final ProjectRepository projectRepository;
    private final ProjectAccessService accessService;
    private final ProjectService projectService;
    private final MunicipalImageValidator imageValidator;
    private final LocalImageStorageService storageService;
    private final int maxFiles;

    public ProjectPhotoService(ProjectRepository projectRepository, ProjectAccessService accessService,
            ProjectService projectService, MunicipalImageValidator imageValidator,
            LocalImageStorageService storageService,
            @Value("${app.upload.max-files:5}") int maxFiles) {
        this.projectRepository = projectRepository;
        this.accessService = accessService;
        this.projectService = projectService;
        this.imageValidator = imageValidator;
        this.storageService = storageService;
        this.maxFiles = maxFiles;
    }

    public MunicipalProject upload(Long projectId, MultipartFile[] files, User actor) {
        MunicipalProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Project not found"));
        accessService.requireMutationAccess(actor, project);
        if (files == null || files.length == 0) throw new IllegalArgumentException("At least one photo is required");
        if (files.length > maxFiles) {
            throw new IllegalArgumentException("A maximum of " + maxFiles + " photos is allowed per request");
        }

        List<MunicipalImageValidator.ValidatedImage> images = Arrays.stream(files).map(imageValidator::validate).toList();
        List<LocalImageStorageService.StoredImage> created = new ArrayList<>();
        List<String> urls = new ArrayList<>(project.getPhotoUrls() == null ? List.of() : project.getPhotoUrls());
        try {
            for (MunicipalImageValidator.ValidatedImage image : images) {
                LocalImageStorageService.StoredImage stored = storageService.store(image, null);
                created.add(stored);
                urls.add(stored.url());
            }
            project.setPhotoUrls(urls);
            MunicipalProject saved = projectRepository.save(project);
            projectService.broadcastProjectEvent("PHOTOS_UPDATED", saved);
            return saved;
        } catch (Exception exception) {
            created.forEach(stored -> {
                try {
                    storageService.delete(stored);
                } catch (RuntimeException cleanupException) {
                    log.warn("Failed to remove stored image {} after upload rollback", stored.url(), cleanupException);
                }
            });
            if (exception instanceof RuntimeException runtime) throw runtime;
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "Photo upload failed");
        }
    }

}
