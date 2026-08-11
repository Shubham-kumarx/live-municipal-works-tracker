package com.municipal.tracker.repository;

import com.municipal.tracker.model.User;
import com.municipal.tracker.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    List<User> findByRole(Role role);

    List<User> findByWardId(Long wardId);

    List<User> findByWardIdAndRole(Long wardId, Role role);
}