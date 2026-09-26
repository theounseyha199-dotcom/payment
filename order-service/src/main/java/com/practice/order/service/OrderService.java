package com.practice.order.service;

import com.practice.order.client.CatalogClient;
import com.practice.order.client.CatalogClient.ProductSummary;
import com.practice.order.dto.CreateOrderRequest;
import com.practice.order.dto.OrderResponse;
import com.practice.order.dto.OrderPaymentContextResponse;
import com.practice.order.dto.PageResponse;
import com.practice.order.dto.OrderStatsResponse;
import com.practice.order.entity.PurchaseOrder;
import com.practice.order.entity.OrderStatus;
import com.practice.order.repository.OrderRepository;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.util.List;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
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
        return createForOwner(request, null);
    }

    public OrderResponse createForOwner(CreateOrderRequest request, String subject) {
        catalog.requireUser(request.userId());
        ProductSummary product = catalog.requireProduct(request.productId());
        BigDecimal total = product.price().multiply(BigDecimal.valueOf(request.quantity()));
        if (total.compareTo(MAX_TOTAL) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Order total is too large. Please reduce the quantity.");
        }
        return OrderResponse.from(orders.save(new PurchaseOrder(
                request.userId(), request.productId(), request.quantity(), product.price(), subject)));
    }

    public OrderResponse get(Long id) {
        PurchaseOrder order = orders.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        return OrderResponse.from(order);
    }

    public OrderPaymentContextResponse paymentContext(Long id) {
        PurchaseOrder order = orders.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        return OrderPaymentContextResponse.from(order);
    }

    public OrderResponse getOwned(Long id, String subject) {
        PurchaseOrder order = orders.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        if (!subject.equals(order.getOwnerSubject())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied.");
        }
        return OrderResponse.from(order);
    }

    public List<OrderResponse> listOwned(String subject) {
        return orders.findAllByOwnerSubject(subject).stream().map(OrderResponse::from).toList();
    }

    public List<OrderResponse> list() {
        return orders.findAll().stream().map(OrderResponse::from).toList();
    }

    public PageResponse<OrderResponse> adminList(Long orderId, Long userId, OrderStatus status,
                                                  LocalDate from, LocalDate to, Pageable pageable) {
        Specification<PurchaseOrder> specification = (root, query, builder) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (orderId != null) predicates.add(builder.equal(root.get("id"), orderId));
            if (userId != null) predicates.add(builder.equal(root.get("userId"), userId));
            if (status == OrderStatus.PENDING_PAYMENT) {
                predicates.add(builder.or(builder.equal(root.get("status"), OrderStatus.PENDING_PAYMENT),
                        builder.isNull(root.get("status"))));
            } else if (status != null) {
                predicates.add(builder.equal(root.get("status"), status));
            }
            if (from != null) predicates.add(builder.greaterThanOrEqualTo(root.get("createdAt"), startOf(from)));
            if (to != null) predicates.add(builder.lessThan(root.get("createdAt"), startOf(to.plusDays(1))));
            return predicates.isEmpty() ? builder.conjunction() : builder.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        Page<OrderResponse> page = orders.findAll(specification, pageable).map(OrderResponse::from);
        return PageResponse.from(page);
    }

    public OrderStatsResponse adminStats() {
        long total = orders.count();
        long pending = orders.countByStatus(OrderStatus.PENDING_PAYMENT) + orders.countByStatusIsNull();
        long paid = orders.countByStatus(OrderStatus.PAID);
        long cancelled = orders.countByStatus(OrderStatus.CANCELLED);
        return new OrderStatsResponse(total, orders.countByCreatedAtGreaterThanEqual(startOf(LocalDate.now())), pending, paid, cancelled);
    }

    private Instant startOf(LocalDate date) {
        return date.atStartOfDay(ZoneId.systemDefault()).toInstant();
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
