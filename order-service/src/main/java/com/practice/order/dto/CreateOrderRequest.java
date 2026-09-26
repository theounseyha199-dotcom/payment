package com.practice.order.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateOrderRequest(
        @NotNull @Positive Long userId,
        @NotNull @Positive Long productId,
        @Positive int quantity
) { }
