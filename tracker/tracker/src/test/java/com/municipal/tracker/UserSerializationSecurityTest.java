package com.municipal.tracker;

import com.municipal.tracker.model.Role;
import com.municipal.tracker.model.User;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class UserSerializationSecurityTest {
    @Test
    void passwordHashIsAbsentFromJsonAndStringOutput() {
        User user = new User();
        user.setEmail("citizen@example.test");
        user.setPassword("sensitive-password-hash");
        user.setFullName("Citizen");
        user.setPhone("9999999999");
        user.setRole(Role.CITIZEN);
        user.setActive(true);

        assertThat(JsonMapper.builder().build().writeValueAsString(user))
                .doesNotContain("password", "sensitive-password-hash");
        assertThat(user.toString()).doesNotContain("sensitive-password-hash");
    }
}
