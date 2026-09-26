package com.practice.payment.dto;

import java.math.BigDecimal;
import java.time.Instant;
import com.practice.payment.entity.PaymentStatus;

public record QrPaymentResponse(Long paymentId, Long orderId, BigDecimal amount, String currency,
                                String qr, String md5, String imageDataUrl,
                                PaymentStatus paymentStatus, Instant expiresAt) { }
