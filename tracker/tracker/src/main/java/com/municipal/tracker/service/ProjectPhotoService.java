package com.municipal.tracker.service;

import com.municipal.tracker.model.MunicipalProject;
import com.municipal.tracker.model.User;
import com.municipal.tracker.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

import static org.springframework.http.HttpStatus.*;

@Service
@RequiredArgsConstructor
public class ProjectPhotoService {
    private final ProjectRepository projectRepository;
    private final ProjectAccessService accessService;
    private final ProjectService projectService;
    private final MunicipalImageValidator imageValidator;

    public MunicipalProject upload(Long projectId, MultipartFile[] files, User actor) {
        MunicipalProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Project not found"));
        accessService.requireMutationAccess(actor, project);
        if (files == null || files.length == 0) throw new IllegalArgumentException("At least one photo is required");

        List<MunicipalImageValidator.ValidatedImage> images = Arrays.stream(files).map(imageValidator::validate).toList();
        Path uploadDir = Path.of("uploads").toAbsolutePath().normalize();
        List<Path> created = new ArrayList<>();
        List<String> urls = new ArrayList<>(project.getPhotoUrls() == null ? List.of() : project.getPhotoUrls());
        try {
            Files.createDirectories(uploadDir);
            for (MunicipalImageValidator.ValidatedImage image : images) {
                String filename = UUID.randomUUID() + "." + image.extension();
                Path destination = uploadDir.resolve(filename).normalize();
                if (!destination.startsWith(uploadDir)) throw new IllegalArgumentException("Invalid filename");
                Files.write(destination, image.bytes(), StandardOpenOption.CREATE_NEW);
                created.add(destination);
                urls.add("/uploads/" + filename);
            }
            project.setPhotoUrls(urls);
            MunicipalProject saved = projectRepository.save(project);
            projectService.broadcastProjectEvent("PHOTOS_UPDATED", saved);
            return saved;
        } catch (Exception exception) {
            created.forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException ignored) { }
            });
            if (exception instanceof RuntimeException runtime) throw runtime;
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "Photo upload failed");
        }
    }

}
