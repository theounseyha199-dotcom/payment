package com.practice.user.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.practice.user.SecurityConfig;
import com.practice.user.dto.UserStatsResponse;
import com.practice.user.service.UserService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminUserController.class)
@Import({SecurityConfig.class, AdminUserSecurityTest.WebSecurity.class})
class AdminUserSecurityTest {
    @TestConfiguration
    @EnableWebSecurity
    static class WebSecurity { }

    @Autowired MockMvc mvc;
    @MockitoBean UserService users;
    @MockitoBean JwtDecoder decoder;

    @BeforeEach
    void tokens() {
        when(decoder.decode(anyString())).thenAnswer(call -> {
            String token = call.getArgument(0);
            return Jwt.withTokenValue(token).header("alg", "none").subject("test-user")
                    .claim("realm_access", Map.of("roles", List.of("admin".equals(token) ? "ADMIN" : "USER")))
                    .build();
        });
    }

    @Test
    void anonymousCannotReadAdminUsers() throws Exception {
        mvc.perform(get("/admin/users/stats")).andExpect(status().isUnauthorized());
    }

    @Test
    void userCannotReadAdminUsers() throws Exception {
        mvc.perform(get("/admin/users/stats").header("Authorization", "Bearer user"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanReadUserStats() throws Exception {
        when(users.adminStats()).thenReturn(new UserStatsResponse(1, 1, 0));
        mvc.perform(get("/admin/users/stats").header("Authorization", "Bearer admin"))
                .andExpect(status().isOk());
    }
}
