package com.municipal.tracker.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "complaints", indexes = {
        @Index(name = "idx_complaint_reporting_user", columnList = "reporting_user_id"),
        @Index(name = "idx_complaint_status", columnList = "status"),
        @Index(name = "idx_complaint_municipal_project", columnList = "municipal_project_id")
})
public class Complaint {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "reporting_user_id", nullable = false)
    private User reportingUser;

    @Column(nullable = false)
    private String imageUrl;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private String locationAddress;

    private Double latitude;
    private Double longitude;

    @Enumerated(EnumType.STRING)
    private ComplaintIssueType aiPredictedIssueType;

    private Double aiConfidence;
    private String aiConfidenceLevel;

    @Enumerated(EnumType.STRING)
    private ComplaintSeverity aiSuggestedSeverity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ComplaintIssueType finalIssueType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ComplaintSeverity finalSeverity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ComplaintPredictionState predictionState;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ComplaintStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "municipal_project_id")
    private MunicipalProject municipalProject;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) status = ComplaintStatus.SUBMITTED;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
