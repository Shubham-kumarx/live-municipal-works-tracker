package com.municipal.tracker.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

@Service
public class LocalImageStorageService {
    private final Path uploadRoot;

    public LocalImageStorageService(@Value("${app.upload.root:uploads}") String uploadRoot) {
        this.uploadRoot = Path.of(uploadRoot).toAbsolutePath().normalize();
    }

    public StoredImage store(MunicipalImageValidator.ValidatedImage image, String subdirectory) {
        Path directory = subdirectory == null || subdirectory.isBlank()
                ? uploadRoot : uploadRoot.resolve(subdirectory).normalize();
        if (!directory.startsWith(uploadRoot)) {
            throw new IllegalArgumentException("Invalid image destination");
        }

        String filename = UUID.randomUUID() + "." + image.extension();
        Path destination = directory.resolve(filename).normalize();
        if (!destination.startsWith(directory)) {
            throw new IllegalArgumentException("Invalid image destination");
        }

        try {
            Files.createDirectories(directory);
            Files.write(destination, image.bytes(), StandardOpenOption.CREATE_NEW);
            String relative = uploadRoot.relativize(destination).toString().replace('\\', '/');
            return new StoredImage(destination, "/uploads/" + relative);
        } catch (IOException exception) {
            throw new IllegalStateException("Image could not be stored", exception);
        }
    }

    public void delete(StoredImage image) {
        if (image == null || image.path() == null) return;
        Path path = image.path().toAbsolutePath().normalize();
        if (!path.startsWith(uploadRoot)) {
            throw new IllegalArgumentException("Invalid image destination");
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Stored image could not be deleted", exception);
        }
    }

    public record StoredImage(Path path, String url) { }
}
