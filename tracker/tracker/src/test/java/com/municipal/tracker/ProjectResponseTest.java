package com.municipal.tracker;

import com.municipal.tracker.dto.ProjectResponse;
import com.municipal.tracker.model.MunicipalProject;
import com.municipal.tracker.model.User;
import com.municipal.tracker.model.ProjectImpactLevel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectResponseTest {
    @Test
    void t06MapsUsersToSafeSummaries() {
        User user = new User();
        user.setId(7L); user.setFullName("Worker"); user.setEmail("private@example.test"); user.setPassword("hash");
        MunicipalProject project = new MunicipalProject();
        project.setAssignedWorker(user); project.setCreatedBy(user);
        ProjectResponse response = ProjectResponse.from(project);
        assertThat(response.getAssignedWorker().getFullName()).isEqualTo("Worker");
        assertThat(response.getAssignedWorker()).hasNoNullFieldsOrProperties();
        assertThat(ProjectResponse.UserSummary.class.getDeclaredFields())
                .extracting("name").containsExactlyInAnyOrder("id", "fullName");
    }

    @Test
    void mapsProjectImpactWithoutExposingPersistenceRelationships() {
        MunicipalProject project = new MunicipalProject();
        project.setImpactLevel(ProjectImpactLevel.HIGH);

        assertThat(ProjectResponse.from(project).getImpactLevel()).isEqualTo(ProjectImpactLevel.HIGH);
    }
}
