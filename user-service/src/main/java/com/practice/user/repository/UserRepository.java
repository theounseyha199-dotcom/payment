package com.practice.user.repository;

import com.practice.user.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {
    boolean existsByEmail(String email);
    Optional<User> findByKeycloakSubject(String keycloakSubject);
    long countByKeycloakSubjectIsNotNull();
}
