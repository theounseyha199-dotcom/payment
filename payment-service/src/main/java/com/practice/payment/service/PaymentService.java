package com.practice.payment.service;

import com.practice.payment.client.BakongClient;
import com.practice.payment.client.BakongClient.BakongResponse;
import com.practice.payment.client.BakongClient.Transaction;
import com.practice.payment.client.OrderClient;
import com.practice.payment.client.OrderClient.OrderSummary;
import com.practice.payment.dto.PaymentVerificationResponse;
import com.practice.payment.dto.PaymentResponse;
import com.practice.payment.dto.VerifyPaymentRequest;
import com.practice.payment.entity.PaymentQr;
import com.practice.payment.entity.PaymentStatus;
import com.practice.payment.repository.PaymentQrRepository;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
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
        PaymentQr payment = requirePayment(paymentId);
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
        OrderSummary order = orders.requireOrder(payment.getOrderId());
        BakongResponse response = bakong.checkTransaction(payment.getMd5());
        Transaction transaction = response.data();
        if (response.responseCode() != 0 || transaction == null) {
            payment.markUnconfirmed();
            qrRepository.save(payment);
            return result(payment);
        }
        boolean matches = order.id().equals(payment.getOrderId())
                && (order.status() == null || "PENDING_PAYMENT".equals(order.status()))
                && order.total().compareTo(payment.getAmount()) == 0
                && transaction.amount() != null
                && transaction.amount().compareTo(payment.getAmount()) == 0
                && "KHR".equalsIgnoreCase(payment.getCurrency())
                && "KHR".equalsIgnoreCase(currency)
                && "KHR".equalsIgnoreCase(transaction.currency())
                && (!StringUtils.hasText(payment.getReceivingAccountId())
                    || accountId.equals(payment.getReceivingAccountId()))
                && accountId.equals(transaction.toAccountId())
                && StringUtils.hasText(transaction.hash());
        if (matches) {
            payment.markVerified(transaction.hash(), transaction.fromAccountId(),
                    transaction.toAccountId(), Instant.now());
        } else {
            payment.markMismatch(transaction.hash(), transaction.fromAccountId(),
                    transaction.toAccountId());
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
        expireIfNecessary(payment);
        return PaymentResponse.from(payment);
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
        } catch (RuntimeException exception) {
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
