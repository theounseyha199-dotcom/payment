package com.practice.order.dto;

import com.practice.order.entity.OrderStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateOrderPaymentRequest(@NotNull OrderStatus status) { }
