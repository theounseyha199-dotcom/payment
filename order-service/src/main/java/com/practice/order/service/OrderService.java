package com.practice.order.service;

import com.practice.order.client.CatalogClient;
import com.practice.order.client.CatalogClient.ProductSummary;
import com.practice.order.dto.CreateOrderRequest;
import com.practice.order.dto.OrderResponse;
import com.practice.order.entity.PurchaseOrder;
import com.practice.order.entity.OrderStatus;
import com.practice.order.repository.OrderRepository;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrderService {
    private static final BigDecimal MAX_TOTAL = new BigDecimal("9999999999");
    private final OrderRepository orders;
    private final CatalogClient catalog;

    public OrderService(OrderRepository orders, CatalogClient catalog) {
        this.orders = orders;
        this.catalog = catalog;
    }

    public OrderResponse create(CreateOrderRequest request) {
        catalog.requireUser(request.userId());
        ProductSummary product = catalog.requireProduct(request.productId());
        BigDecimal total = product.price().multiply(BigDecimal.valueOf(request.quantity()));
        if (total.compareTo(MAX_TOTAL) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Order total is too large. Please reduce the quantity.");
        }
        return OrderResponse.from(orders.save(new PurchaseOrder(
                request.userId(), request.productId(), request.quantity(), product.price())));
    }

    public OrderResponse get(Long id) {
        PurchaseOrder order = orders.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        return OrderResponse.from(order);
    }

    public List<OrderResponse> list() {
        return orders.findAll().stream().map(OrderResponse::from).toList();
    }

    @Transactional
    public OrderResponse updatePaymentStatus(Long id, OrderStatus requestedStatus) {
        if (requestedStatus != OrderStatus.PAID) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only PAID is supported for payment updates.");
        }
        PurchaseOrder order = orders.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        if (order.getStatus() == OrderStatus.PAID) {
            return OrderResponse.from(order);
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order cannot be paid in its current status.");
        }
        order.markPaid();
        return OrderResponse.from(orders.save(order));
    }
}
