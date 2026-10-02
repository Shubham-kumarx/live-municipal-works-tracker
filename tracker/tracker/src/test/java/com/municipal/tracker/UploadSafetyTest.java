package com.municipal.tracker;

import com.municipal.tracker.model.MunicipalProject;
import com.municipal.tracker.model.User;
import com.municipal.tracker.repository.ProjectRepository;
import com.municipal.tracker.service.ProjectAccessService;
import com.municipal.tracker.service.ProjectPhotoService;
import com.municipal.tracker.service.ProjectService;
import com.municipal.tracker.service.MunicipalImageValidator;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UploadSafetyTest {
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
                repository, access, mock(ProjectService.class), new MunicipalImageValidator());

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
                repository, access, mock(ProjectService.class), new MunicipalImageValidator());

        MockMultipartFile mismatch = new MockMultipartFile(
                "photos", "photo.jpg", "image/png", new byte[]{1, 2, 3});
        MockMultipartFile corrupt = new MockMultipartFile(
                "photos", "photo.png", "image/png", new byte[]{1, 2, 3});

        assertThatThrownBy(() -> service.upload(1L, new MockMultipartFile[]{mismatch}, new User()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("extension");
        assertThatThrownBy(() -> service.upload(1L, new MockMultipartFile[]{corrupt}, new User()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("corrupt");
    }
}
