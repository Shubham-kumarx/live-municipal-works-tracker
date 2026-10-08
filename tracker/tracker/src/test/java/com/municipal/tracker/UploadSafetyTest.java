package com.municipal.tracker;

import com.municipal.tracker.model.MunicipalProject;
import com.municipal.tracker.model.User;
import com.municipal.tracker.repository.ProjectRepository;
import com.municipal.tracker.service.ProjectAccessService;
import com.municipal.tracker.service.ProjectPhotoService;
import com.municipal.tracker.service.ProjectService;
import com.municipal.tracker.service.MunicipalImageValidator;
import com.municipal.tracker.service.LocalImageStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UploadSafetyTest {

    @Test
    void photoBatchLimitIsEnforcedBeforeValidationOrStorage() {
        ProjectRepository repository = mock(ProjectRepository.class);
        ProjectAccessService access = mock(ProjectAccessService.class);
        MunicipalImageValidator validator = mock(MunicipalImageValidator.class);
        LocalImageStorageService storage = mock(LocalImageStorageService.class);
        when(repository.findById(1L)).thenReturn(Optional.of(new MunicipalProject()));
        ProjectPhotoService service = new ProjectPhotoService(repository, access,
                mock(ProjectService.class), validator, storage, 2);
        User actor = new User();
        MockMultipartFile file = new MockMultipartFile("files", "photo.png", "image/png", new byte[]{1});

        assertThatThrownBy(() -> service.upload(1L,
                new MockMultipartFile[]{file, file, file}, actor))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maximum of 2");
        verifyNoInteractions(validator, storage);
    }
    @Test
    void t22ApprovedMimeTypesAreExplicitlyLimited() throws java.io.IOException {
        assertThat(java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/municipal/tracker/service/MunicipalImageValidator.java")))
                .contains("image/jpeg", "image/png").doesNotContain("image/gif");
    }

    @Test
    void emptyAndOversizedPhotosAreRejected() {
        ProjectRepository repository = mock(ProjectRepository.class);
        ProjectAccessService access = mock(ProjectAccessService.class);
        User actor = new User();
        when(repository.findById(1L)).thenReturn(Optional.of(new MunicipalProject()));
        ProjectPhotoService service = new ProjectPhotoService(
                repository, access, mock(ProjectService.class), new MunicipalImageValidator(),
                mock(LocalImageStorageService.class), 5);

        MockMultipartFile empty = new MockMultipartFile("photos", "photo.png", "image/png", new byte[0]);
        MockMultipartFile oversized = new MockMultipartFile(
                "photos", "photo.png", "image/png", new byte[10 * 1024 * 1024 + 1]);

        assertThatThrownBy(() -> service.upload(1L, new MockMultipartFile[]{empty}, actor))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Empty");
        assertThatThrownBy(() -> service.upload(1L, new MockMultipartFile[]{oversized}, actor))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("10 MB");
        verify(access, org.mockito.Mockito.times(2)).requireMutationAccess(actor, new MunicipalProject());
    }

    @Test
    void mismatchedAndCorruptPhotoContentIsRejected() {
        ProjectRepository repository = mock(ProjectRepository.class);
        ProjectAccessService access = mock(ProjectAccessService.class);
        when(repository.findById(1L)).thenReturn(Optional.of(new MunicipalProject()));
        ProjectPhotoService service = new ProjectPhotoService(
                repository, access, mock(ProjectService.class), new MunicipalImageValidator(),
                mock(LocalImageStorageService.class), 5);

        MockMultipartFile mismatch = new MockMultipartFile(
                "photos", "photo.jpg", "image/png", new byte[]{1, 2, 3});
        MockMultipartFile corrupt = new MockMultipartFile(
                "photos", "photo.png", "image/png", new byte[]{1, 2, 3});

        assertThatThrownBy(() -> service.upload(1L, new MockMultipartFile[]{mismatch}, new User()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("extension");
        assertThatThrownBy(() -> service.upload(1L, new MockMultipartFile[]{corrupt}, new User()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("corrupt");
    }

    @Test
    void storedProjectPhotosAreDeletedWhenPersistenceFails() {
        ProjectRepository repository = mock(ProjectRepository.class);
        ProjectAccessService access = mock(ProjectAccessService.class);
        MunicipalImageValidator validator = mock(MunicipalImageValidator.class);
        LocalImageStorageService storage = mock(LocalImageStorageService.class);
        MunicipalProject project = new MunicipalProject();
        User actor = new User();
        MockMultipartFile file = new MockMultipartFile("files", "photo.png", "image/png", new byte[]{1});
        MunicipalImageValidator.ValidatedImage validated =
                new MunicipalImageValidator.ValidatedImage("png", "image/png", new byte[]{1});
        LocalImageStorageService.StoredImage stored =
                new LocalImageStorageService.StoredImage(Path.of("uploads", "photo.png"), "/uploads/photo.png");
        when(repository.findById(1L)).thenReturn(Optional.of(project));
        when(validator.validate(file)).thenReturn(validated);
        when(storage.store(validated, null)).thenReturn(stored);
        when(repository.save(project)).thenThrow(new IllegalStateException("database unavailable"));
        ProjectPhotoService service = new ProjectPhotoService(
                repository, access, mock(ProjectService.class), validator, storage, 5);

        assertThatThrownBy(() -> service.upload(1L, new MockMultipartFile[]{file}, actor))
                .isInstanceOf(IllegalStateException.class);
        verify(storage).delete(stored);
    }
}
