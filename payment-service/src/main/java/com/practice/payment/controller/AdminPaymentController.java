package com.practice.payment.controller;

import com.practice.payment.dto.ApiResult;
import com.practice.payment.dto.AdminPaymentResponse;
import com.practice.payment.dto.PageResponse;
import com.practice.payment.dto.PaymentStatsResponse;
import com.practice.payment.dto.PaymentVerificationResponse;
import com.practice.payment.entity.PaymentStatus;
import com.practice.payment.service.PaymentService;
import com.practice.payment.service.AdminAuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import java.util.Map;

@RestController
@RequestMapping("/admin/payments")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Payments", description = "Administrator payment investigation and retry APIs")
public class AdminPaymentController {
    private final PaymentService payments;
    private final AdminAuditService audit;

    public AdminPaymentController(PaymentService payments, AdminAuditService audit) {
        this.payments = payments; this.audit = audit;
    }

    @GetMapping
    @Operation(summary = "List payments for administrators")
    public ApiResult<PageResponse<AdminPaymentResponse>> list(
            @RequestParam(required = false) Long paymentId, @RequestParam(required = false) Long orderId,
            @RequestParam(required = false) PaymentStatus status, @RequestParam(required = false) String currency,
            @RequestParam(required = false) BigDecimal minAmount, @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        if (from != null && to != null && from.isAfter(to)) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "from must be before to.");
        if (minAmount != null && maxAmount != null && minAmount.compareTo(maxAmount) > 0) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "minAmount must be less than maxAmount.");
        return ApiResult.success("Payments loaded successfully.", payments.adminList(paymentId, orderId, status, currency, minAmount, maxAmount, from, to, pageable(page, size, sort)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a payment for administrators")
    public ApiResult<AdminPaymentResponse> get(@PathVariable Long id) {
        return ApiResult.success("Payment found.", payments.adminGet(id));
    }

    @PostMapping("/{id}/retry-verification")
    public ApiResult<PaymentVerificationResponse> retry(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        PaymentVerificationResponse result = payments.verifyForAdmin(id);
        audit.record(jwt.getSubject(), "PAYMENT_VERIFY_RETRY", id, null, Map.of("status", result.status()));
        return ApiResult.success("Payment verification retried.", result);
    }

    @GetMapping("/stats")
    public ApiResult<PaymentStatsResponse> stats() {
        return ApiResult.success("Payment statistics retrieved.", payments.adminStats());
    }

    private Pageable pageable(int page, int size, String sort) {
        if (page < 0 || size < 1 || size > 100) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "Page must be non-negative and size must be 1-100.");
        String[] parts = sort.split(",", -1);
        if (!java.util.Set.of("id", "orderId", "amount", "currency", "status", "createdAt", "expiresAt", "paidAt").contains(parts[0])) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported sort field.");
        Sort.Direction direction = parts.length > 1 ? Sort.Direction.fromOptionalString(parts[1]).orElse(null) : Sort.Direction.DESC;
        if (direction == null) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "Sort direction must be asc or desc.");
        return PageRequest.of(page, size, Sort.by(direction, parts[0]));
    }
}
