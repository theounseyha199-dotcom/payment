package com.practice.order.controller;

import com.practice.order.dto.CreateOrderRequest;
import com.practice.order.dto.ApiResult;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import com.practice.order.dto.OrderResponse;
import com.practice.order.service.OrderService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
public class OrderController {
    private final OrderService orders;

    public OrderController(OrderService orders) {
        this.orders = orders;
    }

    @PostMapping
    @ApiResponse(responseCode = "201", description = "Order created")
    public ResponseEntity<ApiResult<OrderResponse>> create(@Valid @RequestBody CreateOrderRequest request) {
        OrderResponse order = orders.create(request);
        return ResponseEntity.created(URI.create("/orders/" + order.id()))
                .body(ApiResult.success("Order created successfully.", order));
    }

    @GetMapping("/{id}")
    public ApiResult<OrderResponse> get(@PathVariable Long id) {
        return ApiResult.success("Order found.", orders.get(id));
    }

    @GetMapping
    public ApiResult<List<OrderResponse>> list() {
        return ApiResult.success("Orders loaded successfully.", orders.list());
    }
}
