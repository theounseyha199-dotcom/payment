package com.practice.payment.service;

import com.practice.payment.client.BakongClient;
import com.practice.payment.client.BakongClient.BakongResponse;
import com.practice.payment.client.BakongClient.Transaction;
import com.practice.payment.client.OrderClient;
import com.practice.payment.client.OrderClient.OrderSummary;
import com.practice.payment.dto.PaymentVerificationResponse;
import com.practice.payment.dto.PaymentResponse;
import com.practice.payment.dto.AdminPaymentResponse;
import com.practice.payment.dto.PageResponse;
import com.practice.payment.dto.PaymentStatsResponse;
import com.practice.payment.dto.VerifyPaymentRequest;
import com.practice.payment.entity.PaymentQr;
import com.practice.payment.entity.PaymentStatus;
import com.practice.payment.repository.PaymentQrRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class PaymentService {
    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final EnumSet<PaymentStatus> ACTIVE_STATUSES = EnumSet.of(
            PaymentStatus.PENDING, PaymentStatus.UNCONFIRMED);
    private final OrderClient orders;
    private final BakongClient bakong;
    private final PaymentQrRepository qrRepository;
    private final String accountId;
    private final String currency;

    public PaymentService(OrderClient orders, BakongClient bakong, PaymentQrRepository qrRepository,
                          @Value("${bakong.account-id}") String accountId,
                          @Value("${bakong.currency}") String currency) {
        this.orders = orders;
        this.bakong = bakong;
        this.qrRepository = qrRepository;
        this.accountId = accountId;
        this.currency = currency;
    }

    public PaymentVerificationResponse verify(Long paymentId) {
        return verify(paymentId, false);
    }

    public PaymentVerificationResponse verifyForAdmin(Long paymentId) {
        return verify(paymentId, true);
    }

    private PaymentVerificationResponse verify(Long paymentId, boolean adminLookup) {
        PaymentQr payment = requirePayment(paymentId);
        OrderSummary order = adminLookup
                ? orders.requirePaymentContext(payment.getOrderId())
                : orders.requireOrder(payment.getOrderId());
        if (payment.getStatus() == PaymentStatus.VERIFIED) {
            synchronizeOrder(payment);
            return result(payment);
        }
        expireIfNecessary(payment);
        if (payment.getStatus() == PaymentStatus.MISMATCH
                || payment.getStatus() == PaymentStatus.EXPIRED) {
            return result(payment);
        }
        if (payment.getStatus() != PaymentStatus.PENDING
                && payment.getStatus() != PaymentStatus.UNCONFIRMED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Payment cannot be verified in its current status.");
        }
        if (!StringUtils.hasText(accountId)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Bakong configuration is unavailable.");
        }
        BakongResponse response = bakong.checkTransaction(payment.getMd5());
        Transaction transaction = response.data();
        if (response.responseCode() != 0 || transaction == null) {
            payment.markUnconfirmed();
            qrRepository.save(payment);
            return result(payment);
        }
        String mismatchReason = mismatchReason(payment, order, transaction);
        boolean matches = mismatchReason == null;
        if (matches) {
            payment.markVerified(transaction.hash(), transaction.fromAccountId(),
                    transaction.toAccountId(), Instant.now());
        } else {
            payment.markMismatch(transaction.hash(), transaction.fromAccountId(),
                    transaction.toAccountId(), mismatchReason);
        }
        // Repository save commits before the separate order-service request.
        qrRepository.save(payment);
        if (matches) {
            synchronizeOrder(payment);
        }
        return result(payment);
    }

    public PaymentResponse get(Long paymentId) {
        PaymentQr payment = requirePayment(paymentId);
        orders.requireOrder(payment.getOrderId());
        expireIfNecessary(payment);
        return PaymentResponse.from(payment);
    }

    public PageResponse<AdminPaymentResponse> adminList(Long paymentId, Long orderId, PaymentStatus status,
                                                        String currency, BigDecimal minAmount, BigDecimal maxAmount,
                                                        LocalDate from, LocalDate to, Pageable pageable) {
        Specification<PaymentQr> specification = (root, query, builder) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (paymentId != null) predicates.add(builder.equal(root.get("id"), paymentId));
            if (orderId != null) predicates.add(builder.equal(root.get("orderId"), orderId));
            if (status != null) predicates.add(builder.equal(root.get("status"), status));
            if (StringUtils.hasText(currency)) predicates.add(builder.equal(builder.upper(root.get("currency")), currency.toUpperCase(Locale.ROOT)));
            if (minAmount != null) predicates.add(builder.greaterThanOrEqualTo(root.get("amount"), minAmount));
            if (maxAmount != null) predicates.add(builder.lessThanOrEqualTo(root.get("amount"), maxAmount));
            if (from != null) predicates.add(builder.greaterThanOrEqualTo(root.get("createdAt"), startOf(from)));
            if (to != null) predicates.add(builder.lessThan(root.get("createdAt"), startOf(to.plusDays(1))));
            return predicates.isEmpty() ? builder.conjunction() : builder.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        Page<AdminPaymentResponse> page = qrRepository.findAll(specification, pageable).map(AdminPaymentResponse::from);
        return PageResponse.from(page);
    }

    public AdminPaymentResponse adminGet(Long paymentId) {
        return AdminPaymentResponse.from(requirePayment(paymentId));
    }

    public PaymentStatsResponse adminStats() {
        Instant today = startOf(LocalDate.now());
        Instant week = startOf(LocalDate.now().minusDays(6));
        return new PaymentStatsResponse(qrRepository.count(),
                qrRepository.countByStatus(PaymentStatus.PENDING),
                qrRepository.countByStatus(PaymentStatus.VERIFIED),
                qrRepository.countByStatus(PaymentStatus.UNCONFIRMED),
                qrRepository.countByStatus(PaymentStatus.MISMATCH),
                qrRepository.countByStatus(PaymentStatus.EXPIRED),
                qrRepository.sumAmountByStatusAndPaidAtGreaterThanEqual(PaymentStatus.VERIFIED, today),
                qrRepository.sumAmountByStatusAndPaidAtGreaterThanEqual(PaymentStatus.VERIFIED, week));
    }

    private Instant startOf(LocalDate date) {
        return date.atStartOfDay(ZoneId.systemDefault()).toInstant();
    }

    private String mismatchReason(PaymentQr payment, OrderSummary order, Transaction transaction) {
        if (!StringUtils.hasText(transaction.hash())) return "TRANSACTION_NOT_FOUND";
        if (!order.id().equals(payment.getOrderId()) || (order.status() != null && !"PENDING_PAYMENT".equals(order.status()))) {
            return "ORDER_SYNC_FAILED";
        }
        if (order.total() == null || order.total().compareTo(payment.getAmount()) != 0
                || transaction.amount() == null || transaction.amount().compareTo(payment.getAmount()) != 0) {
            return "AMOUNT_MISMATCH";
        }
        if (!"KHR".equalsIgnoreCase(payment.getCurrency()) || !"KHR".equalsIgnoreCase(currency)
                || !"KHR".equalsIgnoreCase(transaction.currency())) return "CURRENCY_MISMATCH";
        if ((!StringUtils.hasText(payment.getReceivingAccountId()) || accountId.equals(payment.getReceivingAccountId()))
                && accountId.equals(transaction.toAccountId())) return null;
        return "ACCOUNT_MISMATCH";
    }

    private PaymentQr requirePayment(Long paymentId) {
        return qrRepository.findById(paymentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Payment not found."));
    }

    private void expireIfNecessary(PaymentQr payment) {
        if ((payment.getStatus() == PaymentStatus.PENDING
                || payment.getStatus() == PaymentStatus.UNCONFIRMED)
                && Instant.now().isAfter(payment.getExpiresAt())) {
            payment.markExpired();
            qrRepository.save(payment);
        }
    }

    private void synchronizeOrder(PaymentQr payment) {
        qrRepository.expireOtherActivePayments(payment.getOrderId(), payment.getId(),
                ACTIVE_STATUSES, PaymentStatus.EXPIRED);
        try {
            orders.markPaid(payment.getOrderId());
            if ("ORDER_SYNC_FAILED".equals(payment.getFailureReason())) {
                payment.clearOrderSyncFailure();
                qrRepository.save(payment);
            }
        } catch (RuntimeException exception) {
            payment.markOrderSyncFailed();
            qrRepository.save(payment);
            log.error("Payment verified but order update failed. paymentId={}, orderId={}",
                    payment.getId(), payment.getOrderId());
        }
    }

    private PaymentVerificationResponse result(PaymentQr payment) {
        return new PaymentVerificationResponse(payment.getId(), payment.getOrderId(),
                payment.getStatus() == PaymentStatus.VERIFIED, payment.getStatus().name());
    }

    @Deprecated
    public PaymentVerificationResponse verify(VerifyPaymentRequest request) {
        PaymentQr qr = qrRepository.findByMd5(request.md5().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Payment not found."));
        if (!qr.getOrderId().equals(request.orderId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The QR does not belong to this order.");
        }
        return verify(qr.getId());
    }
}
