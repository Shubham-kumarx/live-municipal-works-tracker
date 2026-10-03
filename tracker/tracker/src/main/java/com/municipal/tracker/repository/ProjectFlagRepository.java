package com.municipal.tracker.repository;

import com.municipal.tracker.model.ProjectFlag;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectFlagRepository extends JpaRepository<ProjectFlag, Long> {
    boolean existsByProjectIdAndCitizenId(Long projectId, Long citizenId);
}
