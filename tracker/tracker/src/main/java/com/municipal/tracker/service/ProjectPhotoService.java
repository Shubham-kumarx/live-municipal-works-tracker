package com.municipal.tracker.service;

import com.municipal.tracker.model.MunicipalProject;
import com.municipal.tracker.model.User;
import com.municipal.tracker.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

import static org.springframework.http.HttpStatus.*;

@Service
@RequiredArgsConstructor
public class ProjectPhotoService {
    private static final long MAX_FILE_BYTES = 10L * 1024 * 1024;
    private static final long MAX_PIXELS = 40_000_000L;
    private static final Map<String, Set<String>> MIME_EXTENSIONS = Map.of(
            "image/jpeg", Set.of("jpg", "jpeg"), "image/png", Set.of("png"));

    private final ProjectRepository projectRepository;
    private final ProjectAccessService accessService;
    private final ProjectService projectService;

    public MunicipalProject upload(Long projectId, MultipartFile[] files, User actor) {
        MunicipalProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Project not found"));
        accessService.requireMutationAccess(actor, project);
        if (files == null || files.length == 0) throw new IllegalArgumentException("At least one photo is required");

        List<ValidatedImage> images = Arrays.stream(files).map(this::validate).toList();
        Path uploadDir = Path.of("uploads").toAbsolutePath().normalize();
        List<Path> created = new ArrayList<>();
        List<String> urls = new ArrayList<>(project.getPhotoUrls() == null ? List.of() : project.getPhotoUrls());
        try {
            Files.createDirectories(uploadDir);
            for (ValidatedImage image : images) {
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

    private ValidatedImage validate(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Empty photos are not allowed");
        if (file.getSize() > MAX_FILE_BYTES) throw new ResponseStatusException(PAYLOAD_TOO_LARGE, "Photo exceeds 10 MB");
        String mime = Optional.ofNullable(file.getContentType()).orElse("").toLowerCase(Locale.ROOT);
        Set<String> extensions = MIME_EXTENSIONS.get(mime);
        if (extensions == null) throw new IllegalArgumentException("Only JPEG and PNG photos are allowed");
        String original = Optional.ofNullable(file.getOriginalFilename()).orElse("");
        int dot = original.lastIndexOf('.');
        String extension = dot < 0 ? "" : original.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!extensions.contains(extension)) throw new IllegalArgumentException("Photo extension does not match its MIME type");
        try {
            byte[] bytes = file.getBytes();
            try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw new IllegalArgumentException("Photo is corrupt or unsupported");
                ImageReader reader = readers.next();
                try {
                    reader.setInput(input, true, true);
                    String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                    boolean correct = mime.equals("image/png") ? format.equals("png")
                            : format.equals("jpeg") || format.equals("jpg");
                    if (!correct) throw new IllegalArgumentException("Photo content does not match its type");
                    long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                    if (pixels <= 0 || pixels > MAX_PIXELS) throw new IllegalArgumentException("Photo dimensions are invalid or too large");
                } finally { reader.dispose(); }
            }
            return new ValidatedImage(extension.equals("jpeg") ? "jpg" : extension, bytes);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Photo is corrupt or unreadable");
        }
    }

    private record ValidatedImage(String extension, byte[] bytes) { }
}
