package com.practice.order.dto;

import com.practice.order.entity.PurchaseOrder;
import com.practice.order.entity.OrderStatus;
import java.math.BigDecimal;

public record OrderPaymentContextResponse(Long id, BigDecimal totalAmount, String currency,
                                          OrderStatus status) {
    public static OrderPaymentContextResponse from(PurchaseOrder order) {
        return new OrderPaymentContextResponse(order.getId(), order.getTotal(), "KHR", order.getStatus());
    }
}
