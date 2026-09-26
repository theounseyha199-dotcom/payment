package com.practice.order.controller;

import com.practice.order.dto.ApiResult;
import com.practice.order.dto.OrderResponse;
import com.practice.order.dto.OrderPaymentContextResponse;
import com.practice.order.dto.UpdateOrderPaymentRequest;
import com.practice.order.service.OrderService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Hidden
@RequestMapping("/internal/orders")
public class InternalOrderController {
    private final OrderService orders;

    public InternalOrderController(OrderService orders) {
        this.orders = orders;
    }

    @GetMapping("/{orderId}/payment-context")
    public ApiResult<OrderPaymentContextResponse> paymentContext(@PathVariable Long orderId) {
        return ApiResult.success("Order payment context found.", orders.paymentContext(orderId));
    }

    @PatchMapping("/{orderId}/payment")
    public ApiResult<OrderResponse> updatePayment(@PathVariable Long orderId,
                                                   @Valid @RequestBody UpdateOrderPaymentRequest request) {
        return ApiResult.success("Order payment status updated.",
                orders.updatePaymentStatus(orderId, request.status()));
    }
}
