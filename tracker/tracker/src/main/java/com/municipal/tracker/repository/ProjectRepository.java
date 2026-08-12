package com.municipal.tracker.repository;

import com.municipal.tracker.model.MunicipalProject;
import com.municipal.tracker.model.ProjectStatus;
import com.municipal.tracker.model.ProjectType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ProjectRepository extends JpaRepository<MunicipalProject, Long> {

    // Get all projects in a ward
    List<MunicipalProject> findByWardId(Long wardId);

    // Get all projects in a ward by status
    List<MunicipalProject> findByWardIdAndStatus(Long wardId, ProjectStatus status);

    // Get all projects assigned to a field worker
    List<MunicipalProject> findByAssignedWorkerId(Long workerId);

    // Get all flagged projects
    List<MunicipalProject> findByFlaggedTrue();

    // Get all flagged projects in a ward
    List<MunicipalProject> findByWardIdAndFlaggedTrue(Long wardId);

    // Get projects by type in a ward
    List<MunicipalProject> findByWardIdAndProjectType(
            Long wardId, ProjectType projectType);

    // Get delayed projects in a ward
    List<MunicipalProject> findByWardIdAndStatus(
            Long wardId, ProjectStatus status, org.springframework.data.domain.Sort sort);

    // Dashboard — count projects by status in a ward
    long countByWardIdAndStatus(Long wardId, ProjectStatus status);

    // Dashboard — count total projects in a ward
    long countByWardId(Long wardId);

    // Get all projects for map — only id, name, lat, lng, status
    @Query("SELECT p FROM MunicipalProject p WHERE p.ward.id = :wardId " +
            "ORDER BY p.createdAt DESC")
    List<MunicipalProject> findAllByWardForMap(@Param("wardId") Long wardId);

    // Budget analytics
    @Query("SELECT SUM(p.budgetAllocated) FROM MunicipalProject p " +
            "WHERE p.ward.id = :wardId")
    Double getTotalBudgetAllocatedByWard(@Param("wardId") Long wardId);

    @Query("SELECT SUM(p.budgetSpent) FROM MunicipalProject p " +
            "WHERE p.ward.id = :wardId")
    Double getTotalBudgetSpentByWard(@Param("wardId") Long wardId);
}