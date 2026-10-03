package com.municipal.tracker.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "project_flags",
        uniqueConstraints = @UniqueConstraint(name = "uk_project_flag_project_citizen",
                columnNames = {"project_id", "citizen_id"}),
        indexes = {
                @Index(name = "idx_project_flag_project", columnList = "project_id"),
                @Index(name = "idx_project_flag_citizen", columnList = "citizen_id")
        })
@Getter
@Setter
@NoArgsConstructor
public class ProjectFlag {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private MunicipalProject project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "citizen_id", nullable = false)
    private User citizen;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void created() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
