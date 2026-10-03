package com.municipal.tracker;

import com.municipal.tracker.config.JwtUtil;
import com.municipal.tracker.model.Role;
import com.municipal.tracker.model.User;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtSecurityTest {
    private static final String SECRET = "0123456789012345678901234567890123456789012345678901234567890123";

    @Test
    void generatedTokenContainsOnlyStandardClaimsAndSubject() {
        JwtUtil jwt = jwt(60_000L);
        String token = jwt.generateToken(user("citizen@example.test"));

        assertThat(jwt.extractEmail(token)).isEqualTo("citizen@example.test");
        assertThat(decodedPayload(token)).doesNotContain("fullName", "wardId", "role");
    }

    @Test
    void tamperedTokenIsRejected() {
        JwtUtil jwt = jwt(60_000L);
        String token = jwt.generateToken(user("citizen@example.test"));
        String tampered = token.substring(0, token.length() - 1)
                + (token.endsWith("a") ? "b" : "a");

        assertThatThrownBy(() -> jwt.extractEmail(tampered)).isInstanceOf(JwtException.class);
    }

    @Test
    void tokenMustBelongToTheCurrentDatabaseUser() {
        JwtUtil jwt = jwt(60_000L);
        String token = jwt.generateToken(user("first@example.test"));

        assertThat(jwt.isTokenValid(token, user("second@example.test"))).isFalse();
    }

    @Test
    void expiredTokenIsRejected() throws InterruptedException {
        JwtUtil jwt = jwt(1L);
        String token = jwt.generateToken(user("citizen@example.test"));
        Thread.sleep(5L);

        assertThatThrownBy(() -> jwt.extractEmail(token)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void invalidExpirationConfigurationIsRejectedAtStartup() {
        JwtUtil jwt = jwt(0L);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(jwt, "validateConfiguration"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expiration");
    }

    @Test
    void weakSecretConfigurationIsRejectedAtStartup() {
        JwtUtil jwt = jwt(60_000L);
        ReflectionTestUtils.setField(jwt, "secret", "too-short");

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(jwt, "validateConfiguration"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 UTF-8 bytes");
    }

    private JwtUtil jwt(long expiration) {
        JwtUtil jwt = new JwtUtil();
        ReflectionTestUtils.setField(jwt, "secret", SECRET);
        ReflectionTestUtils.setField(jwt, "expiration", expiration);
        return jwt;
    }

    private User user(String email) {
        User user = new User();
        user.setEmail(email);
        user.setFullName("Citizen");
        user.setRole(Role.CITIZEN);
        user.setActive(true);
        return user;
    }

    private String decodedPayload(String token) {
        return new String(java.util.Base64.getUrlDecoder().decode(token.split("\\.")[1]),
                java.nio.charset.StandardCharsets.UTF_8);
    }
}
