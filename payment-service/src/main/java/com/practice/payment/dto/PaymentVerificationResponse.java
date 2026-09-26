package com.practice.payment.dto;

public record PaymentVerificationResponse(Long paymentId, Long orderId, boolean verified, String status) { }
