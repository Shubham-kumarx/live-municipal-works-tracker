package com.municipal.tracker;

import com.municipal.tracker.service.LocalImageStorageService;
import com.municipal.tracker.service.MunicipalImageValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalImageStorageServiceTest {
    @TempDir Path temporaryDirectory;

    @Test
    void storesAndDeletesAnImageBelowTheConfiguredRoot() throws java.io.IOException {
        LocalImageStorageService service = new LocalImageStorageService(temporaryDirectory.toString());
        MunicipalImageValidator.ValidatedImage image =
                new MunicipalImageValidator.ValidatedImage("png", "image/png", new byte[]{1, 2, 3});

        LocalImageStorageService.StoredImage stored = service.store(image, "complaints");

        assertThat(stored.path()).startsWith(temporaryDirectory.toAbsolutePath());
        assertThat(stored.url()).startsWith("/uploads/complaints/").endsWith(".png");
        assertThat(Files.readAllBytes(stored.path())).containsExactly(1, 2, 3);

        service.delete(stored);
        assertThat(stored.path()).doesNotExist();
    }

    @Test
    void rejectsAStoragePathOutsideTheConfiguredRoot() {
        LocalImageStorageService service = new LocalImageStorageService(temporaryDirectory.toString());
        MunicipalImageValidator.ValidatedImage image =
                new MunicipalImageValidator.ValidatedImage("jpg", "image/jpeg", new byte[]{1});

        assertThatThrownBy(() -> service.store(image, "../outside"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
