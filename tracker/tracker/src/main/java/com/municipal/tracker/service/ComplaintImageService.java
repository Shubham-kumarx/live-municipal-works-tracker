package com.municipal.tracker.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ComplaintImageService {
    private final MunicipalImageValidator imageValidator;

    public StoredComplaintImage store(MultipartFile image) {
        MunicipalImageValidator.ValidatedImage validated = imageValidator.validate(image);
        Path directory = Path.of("uploads", "complaints").toAbsolutePath().normalize();
        String filename = UUID.randomUUID() + "." + validated.extension();
        Path destination = directory.resolve(filename).normalize();
        if (!destination.startsWith(directory)) throw new IllegalArgumentException("Invalid image destination");
        try {
            Files.createDirectories(directory);
            Files.write(destination, validated.bytes(), StandardOpenOption.CREATE_NEW);
            return new StoredComplaintImage(destination, "/uploads/complaints/" + filename);
        } catch (IOException exception) {
            throw new IllegalStateException("Complaint image could not be stored");
        }
    }

    public void delete(StoredComplaintImage image) {
        try {
            Files.deleteIfExists(image.path());
        } catch (IOException ignored) { }
    }

    public record StoredComplaintImage(Path path, String url) { }
}
