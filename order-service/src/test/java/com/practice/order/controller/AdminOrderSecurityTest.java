package com.practice.order.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.practice.order.SecurityConfig;
import com.practice.order.dto.OrderPaymentContextResponse;
import com.practice.order.dto.OrderStatsResponse;
import com.practice.order.entity.OrderStatus;
import com.practice.order.service.OrderService;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({AdminOrderController.class, InternalOrderController.class})
@Import({SecurityConfig.class, AdminOrderSecurityTest.WebSecurity.class})
@TestPropertySource(properties = "internal.service-token=private-test-token")
class AdminOrderSecurityTest {
    @TestConfiguration
    @EnableWebSecurity
    static class WebSecurity { }

    @Autowired MockMvc mvc;
    @MockitoBean OrderService orders;
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
    void anonymousCannotReadAdminOrders() throws Exception {
        mvc.perform(get("/admin/orders/stats")).andExpect(status().isUnauthorized());
    }

    @Test
    void userCannotReadAdminOrders() throws Exception {
        mvc.perform(get("/admin/orders/stats").header("Authorization", "Bearer user"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanReadOrderStats() throws Exception {
        when(orders.adminStats()).thenReturn(new OrderStatsResponse(1, 0, 1, 0, 0));
        mvc.perform(get("/admin/orders/stats").header("Authorization", "Bearer admin"))
                .andExpect(status().isOk());
    }

    @Test
    void invalidInternalTokenIsRejected() throws Exception {
        mvc.perform(get("/internal/orders/1/payment-context")
                        .header("Authorization", "Bearer admin")
                        .header("X-Internal-Service-Token", "wrong-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void validInternalTokenReadsOnlyPaymentContext() throws Exception {
        when(orders.paymentContext(1L)).thenReturn(new OrderPaymentContextResponse(
                1L, new BigDecimal("11000"), "KHR", OrderStatus.PENDING_PAYMENT));
        mvc.perform(get("/internal/orders/1/payment-context")
                        .header("X-Internal-Service-Token", "private-test-token"))
                .andExpect(status().isOk());
    }
}
