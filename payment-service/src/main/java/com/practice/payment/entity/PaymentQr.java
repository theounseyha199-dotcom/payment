package com.practice.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payment_qr")
public class PaymentQr {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "payment_qr_id")
    @SequenceGenerator(name = "payment_qr_id", sequenceName = "payment_qr_id_seq", allocationSize = 1)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String md5;

    @Column(nullable = false)
    private Long orderId;

    // Older QR records did not store the QR text or receiving account.
    @Column(columnDefinition = "text")
    private String qr;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    private String receivingAccountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentStatus status;

    private String bakongHash;
    private String fromAccountId;
    private String toAccountId;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant paidAt;

    @Column(length = 64)
    private String failureReason;

    protected PaymentQr() { }

    public PaymentQr(String md5, Long orderId, String qr, BigDecimal amount, String currency,
                     String receivingAccountId, Instant expiresAt) {
        this.md5 = md5;
        this.orderId = orderId;
        this.qr = qr;
        this.amount = amount;
        this.currency = currency;
        this.receivingAccountId = receivingAccountId;
        this.status = PaymentStatus.PENDING;
        this.createdAt = Instant.now();
        this.expiresAt = expiresAt;
    }

    public Long getId() { return id; }
    public String getMd5() { return md5; }
    public Long getOrderId() { return orderId; }
    public String getQr() { return qr; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getReceivingAccountId() { return receivingAccountId; }
    public PaymentStatus getStatus() { return status; }
    public String getBakongHash() { return bakongHash; }
    public String getFromAccountId() { return fromAccountId; }
    public String getToAccountId() { return toAccountId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getPaidAt() { return paidAt; }
    public String getFailureReason() { return failureReason; }

    public void markVerified(String bakongHash, String fromAccountId, String toAccountId, Instant at) {
        this.status = PaymentStatus.VERIFIED;
        this.bakongHash = bakongHash;
        this.fromAccountId = fromAccountId;
        this.toAccountId = toAccountId;
        this.paidAt = at;
        this.failureReason = null;
    }

    public void markUnconfirmed() {
        this.status = PaymentStatus.UNCONFIRMED;
        this.failureReason = "TRANSACTION_NOT_FOUND";
    }

    public void markMismatch(String bakongHash, String fromAccountId, String toAccountId, String failureReason) {
        this.status = PaymentStatus.MISMATCH;
        this.bakongHash = bakongHash;
        this.fromAccountId = fromAccountId;
        this.toAccountId = toAccountId;
        this.failureReason = failureReason;
    }

    public void markExpired() {
        this.status = PaymentStatus.EXPIRED;
        this.failureReason = "QR_EXPIRED";
    }

    public void markOrderSyncFailed() { this.failureReason = "ORDER_SYNC_FAILED"; }

    public void clearOrderSyncFailure() {
        if ("ORDER_SYNC_FAILED".equals(failureReason)) this.failureReason = null;
    }
}
