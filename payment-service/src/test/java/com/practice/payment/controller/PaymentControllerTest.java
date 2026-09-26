package com.practice.payment.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.when;

import com.practice.payment.dto.PaymentResponse;
import com.practice.payment.entity.PaymentStatus;
import com.practice.payment.exception.ApiExceptionHandler;
import com.practice.payment.service.PaymentService;
import com.practice.payment.service.QrPaymentService;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

class PaymentControllerTest {
    private final PaymentService payments = Mockito.mock(PaymentService.class);
    private final QrPaymentService qrPayments = Mockito.mock(QrPaymentService.class);
    private final org.springframework.test.web.servlet.MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new PaymentController(payments, qrPayments))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();

    @Test
    void getPaymentReturnsSafeStandardEnvelope() throws Exception {
        when(payments.get(1L)).thenReturn(new PaymentResponse(1L, 7L,
                new BigDecimal("11000"), "KHR", PaymentStatus.VERIFIED,
                Instant.parse("2026-09-26T14:00:00Z"), Instant.parse("2026-09-26T13:58:32Z")));

        mvc.perform(get("/payments/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.message").value("Payment found."))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.orderId").value(7))
                .andExpect(jsonPath("$.data.amount").value(11000))
                .andExpect(jsonPath("$.data.currency").value("KHR"))
                .andExpect(jsonPath("$.data.status").value("VERIFIED"))
                .andExpect(jsonPath("$.data.paidAt").exists())
                .andExpect(jsonPath("$.data.md5").doesNotExist())
                .andExpect(jsonPath("$.data.receivingAccountId").doesNotExist())
                .andExpect(jsonPath("$.data.bakongHash").doesNotExist());
    }

    @Test
    void missingPaymentReturnsStandard404() throws Exception {
        when(payments.get(99L)).thenThrow(new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Payment not found."));

        mvc.perform(get("/payments/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Payment not found."))
                .andExpect(jsonPath("$.details").isMap());
    }
}
