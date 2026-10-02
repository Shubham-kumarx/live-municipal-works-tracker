package com.municipal.tracker.service;

import com.municipal.tracker.model.MunicipalProject;
import com.municipal.tracker.model.User;
import com.municipal.tracker.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

import static org.springframework.http.HttpStatus.*;

@Service
@RequiredArgsConstructor
public class ProjectPhotoService {
    private final ProjectRepository projectRepository;
    private final ProjectAccessService accessService;
    private final ProjectService projectService;
    private final MunicipalImageValidator imageValidator;
    private final LocalImageStorageService storageService;

    public MunicipalProject upload(Long projectId, MultipartFile[] files, User actor) {
        MunicipalProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Project not found"));
        accessService.requireMutationAccess(actor, project);
        if (files == null || files.length == 0) throw new IllegalArgumentException("At least one photo is required");

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
                try { storageService.delete(stored); } catch (RuntimeException ignored) { }
            });
            if (exception instanceof RuntimeException runtime) throw runtime;
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "Photo upload failed");
        }
    }

}
