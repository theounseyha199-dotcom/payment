package com.practice.payment.dto;

import com.practice.payment.entity.PaymentQr;
import com.practice.payment.entity.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record AdminPaymentResponse(Long id, Long orderId, BigDecimal amount, String currency,
                                   PaymentStatus status, Instant createdAt, Instant expiresAt,
                                   Instant paidAt, String bakongHash, String fromAccountId,
                                   String toAccountId, String failureReason) {
    public static AdminPaymentResponse from(PaymentQr payment) {
        return new AdminPaymentResponse(payment.getId(), payment.getOrderId(), payment.getAmount(),
                payment.getCurrency(), payment.getStatus(), payment.getCreatedAt(), payment.getExpiresAt(),
                payment.getPaidAt(), payment.getBakongHash(), payment.getFromAccountId(),
                payment.getToAccountId(), payment.getFailureReason());
    }
}
