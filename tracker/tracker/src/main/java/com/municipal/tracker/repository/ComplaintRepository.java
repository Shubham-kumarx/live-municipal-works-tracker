package com.municipal.tracker.repository;

import com.municipal.tracker.model.Complaint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ComplaintRepository extends JpaRepository<Complaint, Long> {
    List<Complaint> findByReportingUserIdOrderByCreatedAtDesc(Long reportingUserId);
    List<Complaint> findByMunicipalProjectId(Long municipalProjectId);
    List<Complaint> findByMunicipalProjectIdOrderByCreatedAtDesc(Long municipalProjectId);
    List<Complaint> findByMunicipalProjectIdIn(List<Long> municipalProjectIds);
    List<Complaint> findAllByOrderByCreatedAtDesc();
    List<Complaint> findByReportingUserWardIdOrderByCreatedAtDesc(Long wardId);
}
