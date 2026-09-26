package com.practice.payment.dto;

import com.practice.payment.entity.PaymentQr;
import com.practice.payment.entity.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(Long id, Long orderId, BigDecimal amount, String currency,
                              PaymentStatus status, Instant expiresAt, Instant paidAt) {
    public static PaymentResponse from(PaymentQr payment) {
        return new PaymentResponse(payment.getId(), payment.getOrderId(), payment.getAmount(),
                payment.getCurrency(), payment.getStatus(), payment.getExpiresAt(), payment.getPaidAt());
    }
}
