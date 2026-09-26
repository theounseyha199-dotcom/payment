package com.practice.order.controller;

import com.practice.order.dto.ApiResult;
import com.practice.order.dto.OrderResponse;
import com.practice.order.dto.PageResponse;
import com.practice.order.dto.OrderStatsResponse;
import com.practice.order.entity.OrderStatus;
import com.practice.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/orders")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Orders", description = "Administrator order investigation APIs")
public class AdminOrderController {
    private final OrderService orders;

    public AdminOrderController(OrderService orders) {
        this.orders = orders;
    }

    @GetMapping
    @Operation(summary = "List all orders for administrators")
    public ApiResult<PageResponse<OrderResponse>> list(
            @RequestParam(required = false) Long orderId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must be before to.");
        }
        return ApiResult.success("Orders loaded successfully.", orders.adminList(orderId, userId, status, from, to,
                pageable(page, size, sort)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get any order for administrators")
    public ApiResult<OrderResponse> get(@PathVariable Long id) {
        return ApiResult.success("Order found.", orders.get(id));
    }

    @GetMapping("/stats")
    @Operation(summary = "Get order statistics for administrators")
    public ApiResult<OrderStatsResponse> stats() {
        return ApiResult.success("Order statistics retrieved.", orders.adminStats());
    }

    private Pageable pageable(int page, int size, String sort) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page must be non-negative and size must be 1-100.");
        }
        String[] parts = sort.split(",", -1);
        if (!java.util.Set.of("id", "userId", "productId", "quantity", "unitPrice", "total", "status", "createdAt").contains(parts[0])) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported sort field.");
        }
        Sort.Direction direction = parts.length > 1 ? Sort.Direction.fromOptionalString(parts[1]).orElse(null) : Sort.Direction.DESC;
        if (direction == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sort direction must be asc or desc.");
        return PageRequest.of(page, size, Sort.by(direction, parts[0]));
    }
}
