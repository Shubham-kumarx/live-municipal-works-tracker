package com.municipal.tracker.service;

import com.municipal.tracker.dto.ComplaintCreateRequest;
import com.municipal.tracker.dto.ComplaintResponse;
import com.municipal.tracker.dto.LinkedComplaintResponse;
import com.municipal.tracker.dto.ComplaintLinkedProjectResponse;
import com.municipal.tracker.dto.ProjectLinkOptionResponse;
import com.municipal.tracker.model.*;
import com.municipal.tracker.repository.ComplaintRepository;
import com.municipal.tracker.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Optional;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@RequiredArgsConstructor
public class ComplaintService {
    private final ComplaintRepository complaintRepository;
    private final ComplaintImageService complaintImageService;
    private final ProjectRepository projectRepository;
    private final ProjectAccessService projectAccessService;

    @Transactional
    public ComplaintResponse create(ComplaintCreateRequest request, MultipartFile image, User actor) {
        if (actor == null || actor.getRole() != Role.CITIZEN) {
            throw new AccessDeniedException("Only citizens can submit complaints");
        }
        validateIssueTypes(request);
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

    @Transactional
    public ComplaintResponse linkToProject(Long complaintId, Long projectId, User actor) {
        Complaint complaint = complaintRepository.findById(complaintId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND,
                        "Complaint not found: " + complaintId));
        MunicipalProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND,
                        "Project not found: " + projectId));
        requireAssociationAccess(actor, complaint, project);
        if (complaint.getMunicipalProject() != null) {
            throw new ResponseStatusException(CONFLICT,
                    "Complaint is already linked to project " + complaint.getMunicipalProject().getId());
        }

        complaint.setMunicipalProject(project);
        return ComplaintResponse.from(complaintRepository.save(complaint));
    }

    @Transactional
    public ComplaintResponse unlinkFromProject(Long complaintId, User actor) {
        Complaint complaint = complaintRepository.findById(complaintId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND,
                        "Complaint not found: " + complaintId));
        MunicipalProject project = complaint.getMunicipalProject();
        if (project == null) {
            throw new ResponseStatusException(CONFLICT, "Complaint is not linked to a project");
        }
        requireAssociationAccess(actor, complaint, project);

        complaint.setMunicipalProject(null);
        return ComplaintResponse.from(complaintRepository.save(complaint));
    }

    @Transactional(readOnly = true)
    public List<LinkedComplaintResponse> getLinkedComplaints(Long projectId, User actor) {
        MunicipalProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND,
                        "Project not found: " + projectId));
        projectAccessService.requireProjectWardAccess(actor, project);
        return complaintRepository.findByMunicipalProjectIdOrderByCreatedAtDesc(projectId).stream()
                .map(LinkedComplaintResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<ComplaintLinkedProjectResponse> getLinkedProject(Long complaintId, User actor) {
        Complaint complaint = complaintRepository.findById(complaintId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND,
                        "Complaint not found: " + complaintId));
        requireComplaintManagementAccess(actor, complaint);
        return Optional.ofNullable(complaint.getMunicipalProject())
                .map(ComplaintLinkedProjectResponse::from);
    }

    private void requireComplaintManagementAccess(User actor, Complaint complaint) {
        if (actor == null || (actor.getRole() != Role.MUNICIPAL_ADMIN
                && actor.getRole() != Role.WARD_OFFICER)) {
            throw new AccessDeniedException("Only authorized administrative users can access complaints");
        }
        if (actor.getRole() == Role.WARD_OFFICER) {
            Ward actorWard = actor.getWard();
            Ward reporterWard = complaint.getReportingUser() == null
                    ? null : complaint.getReportingUser().getWard();
            if (actorWard == null || reporterWard == null
                    || !actorWard.getId().equals(reporterWard.getId())) {
                throw new AccessDeniedException("You cannot access complaints from another ward");
            }
        }
    }

    @Transactional(readOnly = true)
    public List<ComplaintResponse> listForCitizen(User actor) {
        if (actor == null || actor.getRole() != Role.CITIZEN) {
            throw new AccessDeniedException("Only citizens can access their submitted complaints");
        }
        return complaintRepository.findByReportingUserIdOrderByCreatedAtDesc(actor.getId()).stream()
                .map(ComplaintResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ComplaintResponse> listForManager(User actor) {
        requireManager(actor);
        List<Complaint> complaints = actor.getRole() == Role.MUNICIPAL_ADMIN
                ? complaintRepository.findAllByOrderByCreatedAtDesc()
                : complaintRepository.findByReportingUserWardIdOrderByCreatedAtDesc(actor.getWard().getId());
        return complaints.stream().map(ComplaintResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ProjectLinkOptionResponse> listLinkableProjects(User actor) {
        requireManager(actor);
        List<MunicipalProject> projects = actor.getRole() == Role.MUNICIPAL_ADMIN
                ? projectRepository.findAll()
                : projectRepository.findByWardId(actor.getWard().getId());
        return projects.stream().map(ProjectLinkOptionResponse::from).toList();
    }

    private void requireManager(User actor) {
        if (actor == null || (actor.getRole() != Role.MUNICIPAL_ADMIN
                && actor.getRole() != Role.WARD_OFFICER)) {
            throw new AccessDeniedException("Only authorized administrative users can access complaints");
        }
        if (actor.getRole() == Role.WARD_OFFICER && actor.getWard() == null) {
            throw new AccessDeniedException("Ward officer has no assigned ward");
        }
    }

    private void requireAssociationAccess(User actor, Complaint complaint, MunicipalProject project) {
        if (actor == null || (actor.getRole() != Role.MUNICIPAL_ADMIN
                && actor.getRole() != Role.WARD_OFFICER)) {
            throw new AccessDeniedException("Only authorized administrative users can manage complaint links");
        }
        projectAccessService.requireOfficerWardAccess(actor, project);
        if (actor.getRole() == Role.WARD_OFFICER) {
            Ward reporterWard = complaint.getReportingUser() == null
                    ? null : complaint.getReportingUser().getWard();
            if (reporterWard == null || project.getWard() == null
                    || !reporterWard.getId().equals(project.getWard().getId())) {
                throw new AccessDeniedException("Complaint and project must belong to the officer's ward");
            }
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

    private void validateIssueTypes(ComplaintCreateRequest request) {
        if (request.finalIssueType() == null
                || !request.finalIssueType().isSupportedClassification()) {
            throw new IllegalArgumentException(
                    "Final issue type must be DOMESTIC_TRASH, ILLEGAL_PARKING, DAMAGED_SIGN, or POTHOLE");
        }
        if (request.aiPredictedIssueType() != null
                && !request.aiPredictedIssueType().isSupportedClassification()) {
            throw new IllegalArgumentException(
                    "AI predicted issue type must be DOMESTIC_TRASH, ILLEGAL_PARKING, DAMAGED_SIGN, or POTHOLE");
        }
    }
}
