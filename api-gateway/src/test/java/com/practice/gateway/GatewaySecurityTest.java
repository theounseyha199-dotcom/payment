package com.practice.gateway;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest
@Import({SecurityConfig.class, GatewaySecurityTest.WebSecurity.class})
class GatewaySecurityTest {
    @TestConfiguration
    @EnableWebSecurity
    static class WebSecurity { }

    @Autowired MockMvc mvc;
    @MockitoBean JwtDecoder decoder;

    @BeforeEach
    void token() {
        when(decoder.decode(anyString())).thenAnswer(call -> Jwt.withTokenValue(call.getArgument(0))
                .header("alg", "none").subject("admin")
                .claim("realm_access", Map.of("roles", List.of("ADMIN"))).build());
    }

    @Test
    void internalPathIsBlockedEvenForAdmin() throws Exception {
        mvc.perform(get("/internal/orders/1/payment-context")
                        .header("Authorization", "Bearer admin"))
                .andExpect(status().isForbidden());
    }
}
