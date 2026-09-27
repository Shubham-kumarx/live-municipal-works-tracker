package com.municipal.tracker.service;

import com.municipal.tracker.model.*;
import com.municipal.tracker.repository.ProjectRepository;
import com.municipal.tracker.repository.UserRepository;
import com.municipal.tracker.repository.WardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final WardRepository wardRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ProjectAccessService projectAccessService;

    // ── CREATE ──────────────────────────────────
    public MunicipalProject createProject(
            MunicipalProject project,
            Long wardId,
            Long createdById) {

        Ward ward = wardRepository.findById(wardId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND,
                        "Ward not found: " + wardId));

        User createdBy = userRepository.findById(createdById)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND,
                        "User not found: " + createdById));

        projectAccessService.requireWardAccess(createdBy, wardId);

        if (project.getBudgetAllocated() == null || !Double.isFinite(project.getBudgetAllocated())
                || project.getBudgetAllocated() < 0) {
            throw new IllegalArgumentException("Allocated budget must be a finite non-negative number");
        }
        if (project.getExpectedEndDate() != null
                && project.getExpectedEndDate().isBefore(project.getStartDate())) {
            throw new IllegalArgumentException("Expected end date cannot be before start date");
        }

        project.setWard(ward);
        project.setCreatedBy(createdBy);
        project.setStatus(ProjectStatus.SANCTIONED);

        MunicipalProject saved = projectRepository.save(project);

        // Broadcast to all citizens watching this ward's map
        broadcastToWard(wardId, "PROJECT_CREATED", saved);

        return saved;
    }

    // ── GET ALL BY WARD ─────────────────────────
    public List<MunicipalProject> getProjectsByWard(Long wardId) {
        return projectRepository.findAllByWardForMap(wardId);
    }

    // ── GET BY ID ───────────────────────────────
    public Optional<MunicipalProject> getProjectById(Long id) {
        return projectRepository.findById(id);
    }

    // ── GET BY WORKER ───────────────────────────
    public List<MunicipalProject> getProjectsByWorker(Long workerId) {
        return projectRepository.findByAssignedWorkerId(workerId);
    }

    // ── GET FLAGGED ─────────────────────────────
    public List<MunicipalProject> getFlaggedProjects(Long wardId) {
        return projectRepository.findByWardIdAndFlaggedTrue(wardId);
    }

    // ── UPDATE STATUS ───────────────────────────
    public MunicipalProject updateStatus(
            Long projectId,
            ProjectStatus newStatus,
            String progressNote,
            Integer progressPercentage,
            User actor) {

        MunicipalProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND,
                        "Project not found: " + projectId));

        projectAccessService.requireMutationAccess(actor, project);

        if (progressPercentage != null && (progressPercentage < 0 || progressPercentage > 100)) {
            throw new IllegalArgumentException("Progress must be between 0 and 100");
        }
        if (!isAllowedTransition(project.getStatus(), newStatus)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT,
                    "Invalid project status transition from " + project.getStatus() + " to " + newStatus);
        }

        project.setStatus(newStatus);
        project.setLastStatusUpdate(LocalDateTime.now());

        if (progressNote != null) {
            project.setProgressNote(progressNote);
        }
        if (progressPercentage != null) {
            project.setProgressPercentage(progressPercentage);
        }

        // Auto set completion
        if (newStatus == ProjectStatus.COMPLETED) {
            project.setProgressPercentage(100);
            project.setActualEndDate(java.time.LocalDate.now());
        }

        MunicipalProject updated = projectRepository.save(project);

        // ← This is the WebSocket magic
        // Broadcast live update to ALL citizens watching this ward
        broadcastToWard(
                project.getWard().getId(),
                "STATUS_UPDATED",
                updated
        );

        return updated;
    }

    private boolean isAllowedTransition(ProjectStatus current, ProjectStatus next) {
        if (current == next) return true;
        return switch (current) {
            case SANCTIONED -> Set.of(ProjectStatus.IN_PROGRESS, ProjectStatus.CANCELLED).contains(next);
            case IN_PROGRESS -> Set.of(ProjectStatus.DELAYED, ProjectStatus.COMPLETED, ProjectStatus.CANCELLED).contains(next);
            case DELAYED -> Set.of(ProjectStatus.IN_PROGRESS, ProjectStatus.COMPLETED, ProjectStatus.CANCELLED).contains(next);
            case COMPLETED, CANCELLED -> false;
        };
    }

    // ── ASSIGN WORKER ───────────────────────────
    public MunicipalProject assignWorker(Long projectId, Long workerId, User actor) {

        MunicipalProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND,
                        "Project not found: " + projectId));

        projectAccessService.requireOfficerWardAccess(actor, project);

        User worker = userRepository.findById(workerId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND,
                        "Worker not found: " + workerId));

        if (worker.getRole() != Role.FIELD_WORKER) {
            throw new IllegalArgumentException(
                    "User is not a field worker");
        }
        if (!Boolean.TRUE.equals(worker.getActive())) {
            throw new IllegalArgumentException("Worker is inactive");
        }
        if (worker.getWard() == null || project.getWard() == null
                || !worker.getWard().getId().equals(project.getWard().getId())) {
            throw new IllegalArgumentException("Worker must belong to the project's ward");
        }

        project.setAssignedWorker(worker);
        MunicipalProject updated = projectRepository.save(project);

        broadcastToWard(project.getWard().getId(), "WORKER_ASSIGNED", updated);

        return updated;
    }

    // ── FLAG PROJECT ─────────────────────────────
    public MunicipalProject flagProject(Long projectId) {

        MunicipalProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND,
                        "Project not found: " + projectId));

        project.setFlagged(true);
        project.setFlagCount(project.getFlagCount() + 1);

        MunicipalProject updated = projectRepository.save(project);

        broadcastToWard(project.getWard().getId(), "PROJECT_FLAGGED", updated);

        return updated;
    }

    // ── UPDATE BUDGET SPENT ──────────────────────
    public MunicipalProject updateBudgetSpent(
            Long projectId, Double amountSpent, User actor) {

        MunicipalProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND,
                        "Project not found: " + projectId));

        projectAccessService.requireOfficerWardAccess(actor, project);

        if (amountSpent == null || !Double.isFinite(amountSpent) || amountSpent < 0) {
            throw new IllegalArgumentException("Amount spent must be a finite non-negative number");
        }

        project.setBudgetSpent(amountSpent);
        MunicipalProject updated = projectRepository.save(project);
        broadcastToWard(project.getWard().getId(), "BUDGET_UPDATED", updated);
        return updated;
    }

    public MunicipalProject updateImpactLevel(Long projectId, ProjectImpactLevel impactLevel, User actor) {
        MunicipalProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND,
                        "Project not found: " + projectId));

        projectAccessService.requireOfficerWardAccess(actor, project);
        if (impactLevel == null) {
            throw new IllegalArgumentException("Impact level is required");
        }

        project.setImpactLevel(impactLevel);
        MunicipalProject updated = projectRepository.save(project);
        broadcastToWard(project.getWard().getId(), "IMPACT_UPDATED", updated);
        return updated;
    }

    public void requireWardAccess(User actor, Long wardId) {
        projectAccessService.requireWardAccess(actor, wardId);
    }

    // ── WARD DASHBOARD STATS ─────────────────────
    public Map<String, Object> getWardStats(Long wardId) {
        Map<String, Object> stats = new HashMap<>();

        stats.put("totalProjects",
                projectRepository.countByWardId(wardId));
        stats.put("sanctioned",
                projectRepository.countByWardIdAndStatus(
                        wardId, ProjectStatus.SANCTIONED));
        stats.put("inProgress",
                projectRepository.countByWardIdAndStatus(
                        wardId, ProjectStatus.IN_PROGRESS));
        stats.put("completed",
                projectRepository.countByWardIdAndStatus(
                        wardId, ProjectStatus.COMPLETED));
        stats.put("delayed",
                projectRepository.countByWardIdAndStatus(
                        wardId, ProjectStatus.DELAYED));
        stats.put("totalBudgetAllocated",
                Optional.ofNullable(projectRepository.getTotalBudgetAllocatedByWard(wardId)).orElse(0.0));
        stats.put("totalBudgetSpent",
                Optional.ofNullable(projectRepository.getTotalBudgetSpentByWard(wardId)).orElse(0.0));

        return stats;
    }

    // ── WEBSOCKET BROADCAST ──────────────────────
    public void broadcastProjectEvent(String eventType, MunicipalProject project) {
        broadcastToWard(project.getWard().getId(), eventType, project);
    }

    private void broadcastToWard(
            Long wardId,
            String eventType,
            MunicipalProject project) {

        Map<String, Object> message = new HashMap<>();
        message.put("eventType", eventType);
        message.put("projectId", project.getId());
        message.put("projectName", project.getProjectName());
        message.put("status", project.getStatus());
        message.put("progressPercentage", project.getProgressPercentage());
        message.put("latitude", project.getLatitude());
        message.put("longitude", project.getLongitude());
        message.put("flagged", project.getFlagged());
        message.put("timestamp", LocalDateTime.now().toString());

        try {
            messagingTemplate.convertAndSend(
                    "/topic/ward/" + wardId + "/projects",
                    (Object) message
            );
        } catch (RuntimeException exception) {
            log.warn("Project event delivery failed: event={}, projectId={}, wardId={}",
                    eventType, project.getId(), wardId);
        }
    }
}
