package com.municipal.tracker.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;

@Service
@RequiredArgsConstructor
public class ComplaintImageService {
    private final MunicipalImageValidator imageValidator;
    private final LocalImageStorageService storageService;

    public StoredComplaintImage store(MultipartFile image) {
        MunicipalImageValidator.ValidatedImage validated = imageValidator.validate(image);
        LocalImageStorageService.StoredImage stored = storageService.store(validated, "complaints");
        return new StoredComplaintImage(stored.path(), stored.url());
    }

    public void delete(StoredComplaintImage image) {
        storageService.delete(new LocalImageStorageService.StoredImage(image.path(), image.url()));
    }

    public record StoredComplaintImage(Path path, String url) { }
}
