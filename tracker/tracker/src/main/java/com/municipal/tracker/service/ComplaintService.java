package com.municipal.tracker.service;

import com.municipal.tracker.dto.ComplaintCreateRequest;
import com.municipal.tracker.dto.ComplaintResponse;
import com.municipal.tracker.model.*;
import com.municipal.tracker.repository.ComplaintRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ComplaintService {
    private final ComplaintRepository complaintRepository;
    private final ComplaintImageService complaintImageService;

    public ComplaintResponse create(ComplaintCreateRequest request, MultipartFile image, User actor) {
        if (actor == null || actor.getRole() != Role.CITIZEN) {
            throw new AccessDeniedException("Only citizens can submit complaints");
        }
        validatePredictionState(request);
        ComplaintImageService.StoredComplaintImage stored = complaintImageService.store(image);
        try {
            Complaint complaint = new Complaint();
            complaint.setReportingUser(actor);
            complaint.setImageUrl(stored.url());
            complaint.setDescription(request.description().trim());
            complaint.setLocationAddress(request.locationAddress().trim());
            complaint.setLatitude(request.latitude());
            complaint.setLongitude(request.longitude());
            complaint.setAiPredictedIssueType(request.aiPredictedIssueType());
            complaint.setAiConfidence(request.aiConfidence());
            complaint.setAiConfidenceLevel(request.aiConfidenceLevel() == null
                    ? null : request.aiConfidenceLevel().name());
            complaint.setAiSuggestedSeverity(request.aiSuggestedSeverity());
            complaint.setFinalIssueType(request.finalIssueType());
            complaint.setFinalSeverity(request.finalSeverity());
            complaint.setPredictionState(request.predictionState());
            complaint.setStatus(ComplaintStatus.SUBMITTED);
            return ComplaintResponse.from(complaintRepository.save(complaint));
        } catch (RuntimeException exception) {
            complaintImageService.delete(stored);
            throw exception;
        }
    }

    private void validatePredictionState(ComplaintCreateRequest request) {
        boolean hasAnyAi = request.aiPredictedIssueType() != null || request.aiConfidence() != null
                || request.aiConfidenceLevel() != null || request.aiSuggestedSeverity() != null;
        boolean hasCompleteAi = request.aiPredictedIssueType() != null && request.aiConfidence() != null
                && request.aiConfidenceLevel() != null && request.aiSuggestedSeverity() != null;

        if (request.predictionState() == ComplaintPredictionState.MANUAL && hasAnyAi) {
            throw new IllegalArgumentException("Manual complaints must not include AI prediction fields");
        }
        if (request.predictionState() != ComplaintPredictionState.MANUAL && !hasCompleteAi) {
            throw new IllegalArgumentException("AI-assisted complaints require complete prediction fields");
        }
        if (request.predictionState() == ComplaintPredictionState.CONFIRMED
                && (request.finalIssueType() != request.aiPredictedIssueType()
                    || request.finalSeverity() != request.aiSuggestedSeverity())) {
            throw new IllegalArgumentException("Confirmed classification must match the AI suggestion");
        }
    }
}
