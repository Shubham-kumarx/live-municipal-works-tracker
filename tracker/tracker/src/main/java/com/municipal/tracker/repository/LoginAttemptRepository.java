package com.municipal.tracker.repository;

import com.municipal.tracker.model.LoginAttempt;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<LoginAttempt> findByLoginKey(String loginKey);

    void deleteByLoginKey(String loginKey);

    @Modifying
    @Query("delete from LoginAttempt attempt where attempt.updatedAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") LocalDateTime cutoff);
}
