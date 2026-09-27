package com.municipal.tracker.repository;

import com.municipal.tracker.model.Complaint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ComplaintRepository extends JpaRepository<Complaint, Long> {
    List<Complaint> findByReportingUserIdOrderByCreatedAtDesc(Long reportingUserId);
    List<Complaint> findByMunicipalProjectId(Long municipalProjectId);
}
