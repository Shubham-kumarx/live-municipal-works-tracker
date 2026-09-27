package com.municipal.tracker;

import com.municipal.tracker.dto.ComplaintCreateRequest;
import com.municipal.tracker.dto.ComplaintResponse;
import com.municipal.tracker.model.*;
import com.municipal.tracker.repository.ComplaintRepository;
import com.municipal.tracker.service.ComplaintImageService;
import com.municipal.tracker.service.ComplaintService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ComplaintServiceTest {
    private final ComplaintRepository repository = mock(ComplaintRepository.class);
    private final ComplaintImageService images = mock(ComplaintImageService.class);
    private final ComplaintService service = new ComplaintService(repository, images);

    @Test
    void createsManualComplaintWithoutAiMetadata() {
        User citizen = citizen();
        when(images.store(any())).thenReturn(new ComplaintImageService.StoredComplaintImage(
                Path.of("uploads/complaints/test.png"), "/uploads/complaints/test.png"));
        when(repository.save(any())).thenAnswer(invocation -> {
            Complaint complaint = invocation.getArgument(0); complaint.setId(12L); return complaint;
        });
        ComplaintCreateRequest request = new ComplaintCreateRequest("Blocked drain", "Test Road",
                null, null, null, null, null, null, ComplaintIssueType.WATERLOGGING,
                ComplaintSeverity.HIGH, ComplaintPredictionState.MANUAL);

        ComplaintResponse response = service.create(request,
                new MockMultipartFile("image", "issue.png", "image/png", new byte[]{1}), citizen);

        assertThat(response.id()).isEqualTo(12L);
        assertThat(response.reportingUserName()).isEqualTo("Citizen");
        assertThat(response.aiPredictedIssueType()).isNull();
    }

    @Test
    void rejectsInconsistentConfirmedPredictionBeforeWritingImage() {
        ComplaintCreateRequest request = new ComplaintCreateRequest("Issue", "Road", null, null,
                ComplaintIssueType.POTHOLE, 0.9, com.municipal.tracker.dto.AIAnalysisResponse.ConfidenceLevel.HIGH,
                ComplaintSeverity.HIGH, ComplaintIssueType.ROAD_CRACK, ComplaintSeverity.HIGH,
                ComplaintPredictionState.CONFIRMED);

        assertThatThrownBy(() -> service.create(request, mock(MockMultipartFile.class), citizen()))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(images, repository);
    }

    private User citizen() {
        User user = new User(); user.setId(5L); user.setFullName("Citizen"); user.setRole(Role.CITIZEN);
        return user;
    }
}
