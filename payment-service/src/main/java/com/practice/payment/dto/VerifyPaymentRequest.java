package com.practice.payment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;

public record VerifyPaymentRequest(
        @NotNull @Positive Long orderId,
        @NotNull @Pattern(regexp = "[a-fA-F0-9]{32}", message = "must be a 32-character MD5 hash") String md5
) { }
