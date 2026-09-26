package com.practice.payment.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.practice.payment.client.BakongClient;
import com.practice.payment.client.OrderClient;
import com.practice.payment.entity.PaymentStatus;
import com.practice.payment.repository.PaymentQrRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PaymentAdminTest {
    private final PaymentQrRepository repository = mock(PaymentQrRepository.class);
    private final PaymentService service = new PaymentService(mock(OrderClient.class), mock(BakongClient.class), repository, "merchant@bakong", "KHR");

    @Test
    void statsCountStatusesAndVerifiedRevenueOnly() {
        when(repository.count()).thenReturn(10L);
        when(repository.countByStatus(any())).thenReturn(1L);
        when(repository.sumAmountByStatusAndPaidAtGreaterThanEqual(eq(PaymentStatus.VERIFIED), any()))
                .thenReturn(new BigDecimal("11000"));
        var stats = service.adminStats();
        assertEquals(10, stats.total());
        assertEquals(new BigDecimal("11000"), stats.revenueToday());
        verify(repository, times(2)).sumAmountByStatusAndPaidAtGreaterThanEqual(eq(PaymentStatus.VERIFIED), any());
    }

    @Test
    void missingPaymentDetailReturns404() {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        var error = assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.adminGet(99L));
        assertEquals(404, error.getStatusCode().value());
    }
}
