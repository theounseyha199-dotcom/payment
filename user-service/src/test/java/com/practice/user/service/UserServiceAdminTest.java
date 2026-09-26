package com.practice.user.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.practice.user.entity.User;
import com.practice.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class UserServiceAdminTest {
    private final UserRepository repository = mock(UserRepository.class);
    private final UserService service = new UserService(repository);

    @Test
    void adminListUsesSearchAndPagination() {
        User user = new User("Seyha", "seyha@example.com", "subject-1");
        when(repository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(java.util.List.of(user), PageRequest.of(0, 20), 1));

        var result = service.adminList("seyha", PageRequest.of(0, 20));

        assertEquals(1, result.totalElements());
        assertTrue(result.first());
        verify(repository).findAll(any(org.springframework.data.jpa.domain.Specification.class), eq(PageRequest.of(0, 20)));
    }

    @Test
    void adminDetailMissingUserIsNotFound() {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        var error = assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.adminGet(99L));
        assertEquals(404, error.getStatusCode().value());
    }

    @Test
    void statsSeparateLinkedAndLegacyProfiles() {
        when(repository.count()).thenReturn(3L);
        when(repository.countByKeycloakSubjectIsNotNull()).thenReturn(2L);
        var stats = service.adminStats();
        assertEquals(3, stats.totalUsers());
        assertEquals(2, stats.linkedToKeycloak());
        assertEquals(1, stats.legacyProfiles());
    }

    @Test
    void existingEmailIsNotAutomaticallyLinkedByMeProfileCreation() {
        when(repository.findByKeycloakSubject("new-subject")).thenReturn(Optional.empty());
        when(repository.existsByEmail("legacy@example.com")).thenReturn(true);
        var jwt = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("token")
                .header("alg", "none").subject("new-subject").claim("email", "legacy@example.com").build();
        var error = assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.me(jwt));
        assertEquals(409, error.getStatusCode().value());
        verify(repository, never()).save(any());
    }
}
