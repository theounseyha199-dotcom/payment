package com.practice.user.dto;

import com.practice.user.entity.User;
import java.time.Instant;

public record AdminUserResponse(
        Long id,
        String name,
        String email,
        String keycloakSubject,
        boolean keycloakLinked,
        Instant createdAt
) {
    public static AdminUserResponse from(User user) {
        return new AdminUserResponse(user.getId(), user.getName(), user.getEmail(),
                user.getKeycloakSubject(), user.getKeycloakSubject() != null, user.getCreatedAt());
    }
}
