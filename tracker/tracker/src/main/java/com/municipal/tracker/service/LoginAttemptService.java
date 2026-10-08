package com.municipal.tracker.service;

import com.municipal.tracker.model.LoginAttempt;
import com.municipal.tracker.repository.LoginAttemptRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.municipal.tracker.exception.TooManyLoginAttemptsException;
import org.springframework.transaction.annotation.Propagation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;

@Service
public class LoginAttemptService {
    private final LoginAttemptRepository repository;
    private final int maxFailures;
    private final Duration window;
    private final Duration blockDuration;

    public LoginAttemptService(LoginAttemptRepository repository,
            @Value("${app.security.login.max-failures:5}") int maxFailures,
            @Value("${app.security.login.window-minutes:15}") long windowMinutes,
            @Value("${app.security.login.block-minutes:15}") long blockMinutes) {
        if (maxFailures < 1 || windowMinutes < 1 || blockMinutes < 1) {
            throw new IllegalArgumentException("Login throttling values must be positive");
        }
        this.repository = repository;
        this.maxFailures = maxFailures;
        this.window = Duration.ofMinutes(windowMinutes);
        this.blockDuration = Duration.ofMinutes(blockMinutes);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void checkAllowed(String email, String remoteAddress) {
        String key = key(email, remoteAddress);
        repository.findByLoginKey(key).ifPresent(attempt -> {
            LocalDateTime now = LocalDateTime.now();
            if (attempt.getBlockedUntil() != null && attempt.getBlockedUntil().isAfter(now)) {
                throw new TooManyLoginAttemptsException();
            }
            if (attempt.getWindowStartedAt().plus(window).isBefore(now)) {
                repository.delete(attempt);
            }
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String email, String remoteAddress) {
        String key = key(email, remoteAddress);
        LocalDateTime now = LocalDateTime.now();
        LoginAttempt attempt = repository.findByLoginKey(key).orElseGet(() -> {
            LoginAttempt created = new LoginAttempt();
            created.setLoginKey(key);
            created.setWindowStartedAt(now);
            return created;
        });
        if (attempt.getWindowStartedAt().plus(window).isBefore(now)) {
            attempt.setWindowStartedAt(now);
            attempt.setFailureCount(0);
            attempt.setBlockedUntil(null);
        }
        attempt.setFailureCount(attempt.getFailureCount() + 1);
        if (attempt.getFailureCount() >= maxFailures) {
            attempt.setBlockedUntil(now.plus(blockDuration));
        }
        repository.save(attempt);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(String email, String remoteAddress) {
        repository.deleteByLoginKey(key(email, remoteAddress));
    }

    String key(String email, String remoteAddress) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        String normalizedAddress = remoteAddress == null || remoteAddress.isBlank()
                ? "unknown" : remoteAddress.trim();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    (normalizedEmail + "|" + normalizedAddress).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
