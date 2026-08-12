package com.municipal.tracker.service;

import com.municipal.tracker.model.*;
import com.municipal.tracker.repository.ProjectRepository;
import com.municipal.tracker.repository.UserRepository;
import com.municipal.tracker.repository.WardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final WardRepository wardRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    // ── CREATE ──────────────────────────────────
    public MunicipalProject createProject(
            MunicipalProject project,
            Long wardId,
            Long createdById) {

        Ward ward = wardRepository.findById(wardId)
                .orElseThrow(() -> new RuntimeException(
                        "Ward not found: " + wardId));

        User createdBy = userRepository.findById(createdById)
                .orElseThrow(() -> new RuntimeException(
                        "User not found: " + createdById));

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
            Integer progressPercentage) {

        MunicipalProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException(
                        "Project not found: " + projectId));

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

    // ── ASSIGN WORKER ───────────────────────────
    public MunicipalProject assignWorker(Long projectId, Long workerId) {

        MunicipalProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException(
                        "Project not found: " + projectId));

        User worker = userRepository.findById(workerId)
                .orElseThrow(() -> new RuntimeException(
                        "Worker not found: " + workerId));

        if (worker.getRole() != Role.FIELD_WORKER) {
            throw new RuntimeException(
                    "User is not a field worker");
        }

        project.setAssignedWorker(worker);
        MunicipalProject updated = projectRepository.save(project);

        broadcastToWard(project.getWard().getId(), "WORKER_ASSIGNED", updated);

        return updated;
    }

    // ── FLAG PROJECT ─────────────────────────────
    public MunicipalProject flagProject(Long projectId) {

        MunicipalProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException(
                        "Project not found: " + projectId));

        project.setFlagged(true);
        project.setFlagCount(project.getFlagCount() + 1);

        MunicipalProject updated = projectRepository.save(project);

        broadcastToWard(project.getWard().getId(), "PROJECT_FLAGGED", updated);

        return updated;
    }

    // ── UPDATE BUDGET SPENT ──────────────────────
    public MunicipalProject updateBudgetSpent(
            Long projectId, Double amountSpent) {

        MunicipalProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException(
                        "Project not found: " + projectId));

        project.setBudgetSpent(amountSpent);
        return projectRepository.save(project);
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
                projectRepository.getTotalBudgetAllocatedByWard(wardId));
        stats.put("totalBudgetSpent",
                projectRepository.getTotalBudgetSpentByWard(wardId));

        return stats;
    }

    // ── WEBSOCKET BROADCAST ──────────────────────
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

        messagingTemplate.convertAndSend(
                "/topic/ward/" + wardId + "/projects",
                (Object) message
        );
    }
}
