package com.practice.order.dto;

import com.practice.order.entity.PurchaseOrder;
import com.practice.order.entity.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record OrderResponse(Long id, Long userId, Long productId, int quantity,
                            BigDecimal unitPrice, BigDecimal totalAmount, String currency,
                            OrderStatus status, Instant createdAt) {
    public static OrderResponse from(PurchaseOrder order) {
        return new OrderResponse(order.getId(), order.getUserId(), order.getProductId(),
                order.getQuantity(), order.getUnitPrice(), order.getTotal(), "KHR",
                order.getStatus(), order.getCreatedAt());
    }
}
