package com.municipal.tracker.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "municipal_projects")
public class MunicipalProject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String projectName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProjectType projectType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProjectStatus status;

    // ── Location ────────────────────────────────
    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Column(nullable = false)
    private String locationAddress;

    // ── Budget ──────────────────────────────────
    @Column(nullable = false)
    private Double budgetAllocated;

    @Column(nullable = false, columnDefinition = "float8 default 0.0")
    private Double budgetSpent;

    // ── Dates ───────────────────────────────────
    @Column(nullable = false)
    private LocalDate startDate;

    private LocalDate expectedEndDate;

    private LocalDate actualEndDate;

    // ── Progress ────────────────────────────────
    @Column(nullable = false, columnDefinition = "integer default 0")
    private Integer progressPercentage;

    @Column(columnDefinition = "TEXT")
    private String progressNote;

    // ── Relationships ───────────────────────────
    @ManyToOne
    @JoinColumn(name = "ward_id", nullable = false)
    private Ward ward;

    @ManyToOne
    @JoinColumn(name = "assigned_worker_id")
    private User assignedWorker;

    @ManyToOne
    @JoinColumn(name = "created_by_id", nullable = false)
    private User createdBy;

    // ── Photo Evidence ──────────────────────────
    @ElementCollection
    @CollectionTable(
            name = "project_photos",
            joinColumns = @JoinColumn(name = "project_id")
    )
    @Column(name = "photo_url")
    private List<String> photoUrls;

    // ── Flags ───────────────────────────────────
    @Column(nullable = false, columnDefinition = "boolean default false")
    private Boolean flagged;

    @Column(nullable = false, columnDefinition = "integer default 0")
    private Integer flagCount;

    // ── Timestamps ──────────────────────────────
    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime lastStatusUpdate;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        lastStatusUpdate = LocalDateTime.now();
        if (status == null) status = ProjectStatus.SANCTIONED;
        if (progressPercentage == null) progressPercentage = 0;
        if (budgetSpent == null) budgetSpent = 0.0;
        if (flagged == null) flagged = false;
        if (flagCount == null) flagCount = 0;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
