package com.municipal.tracker;

import com.municipal.tracker.model.*;
import com.municipal.tracker.repository.ComplaintRepository;
import com.municipal.tracker.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class ComplaintRepositoryTest {
    @Autowired ComplaintRepository complaintRepository;
    @Autowired UserRepository userRepository;

    @Test
    void savesComplaintWithoutExposingOrChangingExistingEntities() {
        User citizen = new User();
        citizen.setEmail("citizen@example.test"); citizen.setPassword("encoded");
        citizen.setFullName("Test Citizen"); citizen.setPhone("9999999999");
        citizen.setRole(Role.CITIZEN); citizen.setActive(true);
        citizen = userRepository.save(citizen);

        Complaint complaint = new Complaint();
        complaint.setReportingUser(citizen); complaint.setImageUrl("/uploads/complaints/test.png");
        complaint.setDescription("Pothole near the crossing"); complaint.setLocationAddress("Test Road");
        complaint.setFinalIssueType(ComplaintIssueType.POTHOLE);
        complaint.setFinalSeverity(ComplaintSeverity.HIGH);
        complaint.setPredictionState(ComplaintPredictionState.MANUAL);

        Complaint saved = complaintRepository.saveAndFlush(complaint);

        assertThat(saved.getId()).isNotNull();
        assertThat(complaintRepository.findByReportingUserIdOrderByCreatedAtDesc(citizen.getId()))
                .extracting(Complaint::getId).containsExactly(saved.getId());
    }
}
