package com.practice.payment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateQrRequest(@NotNull @Positive Long orderId) { }
