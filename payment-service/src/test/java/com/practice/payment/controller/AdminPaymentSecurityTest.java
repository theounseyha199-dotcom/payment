package com.practice.payment.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.practice.payment.SecurityConfig;
import com.practice.payment.dto.PaymentStatsResponse;
import com.practice.payment.service.AdminAuditService;
import com.practice.payment.service.PaymentService;
import java.math.BigDecimal;
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

@WebMvcTest(AdminPaymentController.class)
@Import({SecurityConfig.class, AdminPaymentSecurityTest.WebSecurity.class})
class AdminPaymentSecurityTest {
    @TestConfiguration
    @EnableWebSecurity
    static class WebSecurity { }

    @Autowired MockMvc mvc;
    @MockitoBean PaymentService payments;
    @MockitoBean AdminAuditService audit;
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
    void anonymousCannotReadAdminPayments() throws Exception {
        mvc.perform(get("/admin/payments/stats")).andExpect(status().isUnauthorized());
    }

    @Test
    void userCannotReadAdminPayments() throws Exception {
        mvc.perform(get("/admin/payments/stats").header("Authorization", "Bearer user"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanReadPaymentStats() throws Exception {
        when(payments.adminStats()).thenReturn(new PaymentStatsResponse(1, 1, 0, 0, 0, 0,
                BigDecimal.ZERO, BigDecimal.ZERO));
        mvc.perform(get("/admin/payments/stats").header("Authorization", "Bearer admin"))
                .andExpect(status().isOk());
    }
}
