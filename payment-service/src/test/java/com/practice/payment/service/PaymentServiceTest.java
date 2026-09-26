package com.practice.payment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;

import com.practice.payment.client.BakongClient;
import com.practice.payment.client.BakongClient.BakongResponse;
import com.practice.payment.client.BakongClient.Transaction;
import com.practice.payment.client.OrderClient;
import com.practice.payment.client.OrderClient.OrderSummary;
import com.practice.payment.dto.VerifyPaymentRequest;
import com.practice.payment.entity.PaymentQr;
import com.practice.payment.entity.PaymentStatus;
import com.practice.payment.repository.PaymentQrRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class PaymentServiceTest {
    private static final String MD5 = "0123456789abcdef0123456789abcdef";
    private final OrderClient orders = Mockito.mock(OrderClient.class);
    private final BakongClient bakong = Mockito.mock(BakongClient.class);
    private final PaymentQrRepository qrRepository = Mockito.mock(PaymentQrRepository.class);
    private final PaymentService service = new PaymentService(orders, bakong, qrRepository, "merchant@bakong", "KHR");
    private final PaymentQr qr = new PaymentQr(MD5, 1L, "000201...", new BigDecimal("40"),
            "KHR", "merchant@bakong", Instant.now().plusSeconds(300));

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(qr, "id", 1L);
        when(qrRepository.findById(1L)).thenReturn(Optional.of(qr));
        when(qrRepository.findByMd5(MD5)).thenReturn(Optional.of(qr));
        when(orders.requireOrder(1L)).thenReturn(new OrderSummary(1L, new BigDecimal("40")));
    }

    @Test
    void verifiesOnlyMatchingTransactionAndStoresResult() {
        when(bakong.checkTransaction(MD5)).thenReturn(new BakongResponse(0, null, "OK",
                new Transaction("hash", "payer@bakong", "merchant@bakong", "KHR", new BigDecimal("40.00"), null)));

        var result = service.verify(1L);

        assertTrue(result.verified());
        assertEquals(1L, result.paymentId());
        assertEquals("VERIFIED", result.status());
        assertEquals(PaymentStatus.VERIFIED, qr.getStatus());
        assertTrue(qr.getPaidAt() != null);
        assertEquals("hash", qr.getBakongHash());
        assertEquals("payer@bakong", qr.getFromAccountId());
        assertEquals("merchant@bakong", qr.getToAccountId());
        verify(orders).markPaid(1L);
        verify(qrRepository).save(qr);
        var sequence = Mockito.inOrder(qrRepository, orders);
        sequence.verify(qrRepository).save(qr);
        sequence.verify(orders).markPaid(1L);
        verify(qrRepository).expireOtherActivePayments(Mockito.eq(1L), Mockito.eq(1L),
                Mockito.anyCollection(), Mockito.eq(PaymentStatus.EXPIRED));
    }

    @Test
    void rejectsTransactionForDifferentAccount() {
        when(bakong.checkTransaction(MD5)).thenReturn(new BakongResponse(0, null, "OK",
                new Transaction("hash", "payer@bakong", "another@bakong", "KHR", new BigDecimal("40"), null)));

        var result = service.verify(1L);

        assertFalse(result.verified());
        assertEquals("MISMATCH", result.status());
        verify(orders, never()).markPaid(1L);
        assertEquals(PaymentStatus.MISMATCH, qr.getStatus());
        verify(qrRepository).save(qr);
    }

    @Test
    void missingBakongAccountReturns503WithoutCallingProvider() {
        PaymentService unconfigured = new PaymentService(orders, bakong, qrRepository, "", "KHR");

        var error = assertThrows(ResponseStatusException.class, () -> unconfigured.verify(1L));

        assertEquals(503, error.getStatusCode().value());
        assertEquals("Bakong configuration is unavailable.", error.getReason());
        verify(bakong, never()).checkTransaction(MD5);
    }

    @Test
    void rejectsTransactionForDifferentCurrencyWithoutRecheckingMismatch() {
        when(bakong.checkTransaction(MD5)).thenReturn(new BakongResponse(0, null, "OK",
                new Transaction("hash", "payer@bakong", "merchant@bakong", "USD", new BigDecimal("40"), null)));
        assertEquals("MISMATCH", service.verify(1L).status());

        assertEquals("MISMATCH", service.verify(1L).status());
        verify(bakong, times(1)).checkTransaction(MD5);
        verify(orders, never()).markPaid(1L);
    }

    @Test
    void rejectsTransactionForDifferentAmount() {
        when(bakong.checkTransaction(MD5)).thenReturn(new BakongResponse(0, null, "OK",
                new Transaction("hash", "payer@bakong", "merchant@bakong", "KHR", new BigDecimal("39"), null)));

        assertEquals("MISMATCH", service.verify(1L).status());
        verify(orders, never()).markPaid(1L);
    }

    @Test
    void rejectsTransactionWhenSavedAccountDiffersFromConfiguration() {
        PaymentQr otherAccount = new PaymentQr(MD5, 1L, "000201...", new BigDecimal("40"),
                "KHR", "other@bakong", Instant.now().plusSeconds(300));
        ReflectionTestUtils.setField(otherAccount, "id", 1L);
        when(qrRepository.findById(1L)).thenReturn(Optional.of(otherAccount));
        when(bakong.checkTransaction(MD5)).thenReturn(new BakongResponse(0, null, "OK",
                new Transaction("hash", "payer@bakong", "merchant@bakong", "KHR", new BigDecimal("40"), null)));

        assertEquals("MISMATCH", service.verify(1L).status());
        verify(orders, never()).markPaid(1L);
    }

    @Test
    void rejectsTransactionWhenOrderIsCancelled() {
        when(orders.requireOrder(1L)).thenReturn(new OrderSummary(1L, new BigDecimal("40"), "CANCELLED"));
        when(bakong.checkTransaction(MD5)).thenReturn(new BakongResponse(0, null, "OK",
                new Transaction("hash", "payer@bakong", "merchant@bakong", "KHR", new BigDecimal("40"), null)));

        assertEquals("MISMATCH", service.verify(1L).status());
        verify(orders, never()).markPaid(1L);
    }

    @Test
    void rejectsTransactionWhenOrderResponseDoesNotMatchPaymentOrder() {
        when(orders.requireOrder(1L)).thenReturn(new OrderSummary(2L, new BigDecimal("40"), "PENDING_PAYMENT"));
        when(bakong.checkTransaction(MD5)).thenReturn(new BakongResponse(0, null, "OK",
                new Transaction("hash", "payer@bakong", "merchant@bakong", "KHR", new BigDecimal("40"), null)));

        assertEquals("MISMATCH", service.verify(1L).status());
        verify(orders, never()).markPaid(1L);
    }

    @Test
    void rejectsTransactionWithoutBakongHash() {
        when(bakong.checkTransaction(MD5)).thenReturn(new BakongResponse(0, null, "OK",
                new Transaction(null, "payer@bakong", "merchant@bakong", "KHR", new BigDecimal("40"), null)));

        assertEquals("MISMATCH", service.verify(1L).status());
        verify(orders, never()).markPaid(1L);
    }

    @Test
    void doesNotVerifyMissingTransaction() {
        when(bakong.checkTransaction(MD5)).thenReturn(new BakongResponse(1, 1, "Not found", null));

        var result = service.verify(1L);

        assertFalse(result.verified());
        assertEquals("UNCONFIRMED", result.status());
        assertEquals(PaymentStatus.UNCONFIRMED, qr.getStatus());
    }

    @Test
    void expiresPendingQrWithoutCallingBakong() {
        PaymentQr expired = new PaymentQr(MD5, 1L, "000201...", new BigDecimal("40"),
                "KHR", "merchant@bakong", Instant.now().minusSeconds(1));
        ReflectionTestUtils.setField(expired, "id", 1L);
        when(qrRepository.findById(1L)).thenReturn(Optional.of(expired));

        var result = service.verify(1L);

        assertEquals("EXPIRED", result.status());
        assertEquals(PaymentStatus.EXPIRED, expired.getStatus());
        verify(qrRepository).save(expired);
        assertEquals("EXPIRED", service.verify(1L).status());
        verify(bakong, never()).checkTransaction(MD5);
        verify(orders, never()).markPaid(1L);
    }

    @Test
    void getPaymentExpiresPendingQrAndReturnsSafeSummary() {
        PaymentQr expired = new PaymentQr(MD5, 1L, "000201...", new BigDecimal("40"),
                "KHR", "merchant@bakong", Instant.now().minusSeconds(1));
        ReflectionTestUtils.setField(expired, "id", 1L);
        when(qrRepository.findById(1L)).thenReturn(Optional.of(expired));

        var result = service.get(1L);

        assertEquals(1L, result.id());
        assertEquals(PaymentStatus.EXPIRED, result.status());
        assertEquals("KHR", result.currency());
        assertEquals(new BigDecimal("40"), result.amount());
        verify(qrRepository).save(expired);
    }

    @Test
    void getPaymentNeverExpiresAlreadyVerifiedPayment() {
        PaymentQr oldVerified = new PaymentQr(MD5, 1L, "000201...", new BigDecimal("40"),
                "KHR", "merchant@bakong", Instant.now().minusSeconds(1));
        ReflectionTestUtils.setField(oldVerified, "id", 1L);
        oldVerified.markVerified("hash", "payer@bakong", "merchant@bakong", Instant.now());
        when(qrRepository.findById(1L)).thenReturn(Optional.of(oldVerified));

        var result = service.get(1L);

        assertEquals(PaymentStatus.VERIFIED, result.status());
        assertTrue(result.paidAt() != null);
        verify(qrRepository, never()).save(oldVerified);
    }

    @Test
    void getMissingPaymentReturns404() {
        when(qrRepository.findById(99L)).thenReturn(Optional.empty());

        var error = assertThrows(ResponseStatusException.class, () -> service.get(99L));

        assertEquals(404, error.getStatusCode().value());
        assertEquals("Payment not found.", error.getReason());
    }

    @Test
    void rejectsMd5ForAnotherOrderBeforeCallingBakong() {
        var error = assertThrows(ResponseStatusException.class,
                () -> service.verify(new VerifyPaymentRequest(2L, MD5)));
        assertEquals(400, error.getStatusCode().value());
        verify(bakong, never()).checkTransaction(MD5);
    }

    @Test
    void missingPaymentIdReturns404() {
        when(qrRepository.findById(99L)).thenReturn(Optional.empty());

        var error = assertThrows(ResponseStatusException.class, () -> service.verify(99L));

        assertEquals(404, error.getStatusCode().value());
        assertEquals("Payment not found.", error.getReason());
        verify(bakong, never()).checkTransaction(MD5);
    }

    @Test
    void returnsStoredVerifiedResultWithoutCallingBakongAgain() {
        qr.markVerified("hash", "payer@bakong", "merchant@bakong", Instant.now());

        var result = service.verify(1L);

        assertTrue(result.verified());
        verify(orders).markPaid(1L);
        verify(qrRepository).expireOtherActivePayments(Mockito.eq(1L), Mockito.eq(1L),
                Mockito.anyCollection(), Mockito.eq(PaymentStatus.EXPIRED));
        verify(bakong, never()).checkTransaction(MD5);
    }

    @Test
    void adminRetryUsesInternalContextWithoutCustomerOrderLookup() {
        when(orders.requirePaymentContext(1L))
                .thenReturn(new OrderSummary(1L, new BigDecimal("40"), "PENDING_PAYMENT"));
        when(bakong.checkTransaction(MD5)).thenReturn(new BakongResponse(0, null, "OK",
                new Transaction("hash", "payer@bakong", "merchant@bakong", "KHR", new BigDecimal("40"), null)));

        assertTrue(service.verifyForAdmin(1L).verified());

        verify(orders).requirePaymentContext(1L);
        verify(orders, never()).requireOrder(1L);
        verify(orders).markPaid(1L);
        verify(bakong).checkTransaction(MD5);
    }

    @Test
    void adminRetryOfVerifiedPaymentSkipsBakong() {
        when(orders.requirePaymentContext(1L))
                .thenReturn(new OrderSummary(1L, new BigDecimal("40"), "PENDING_PAYMENT"));
        qr.markVerified("hash", "payer@bakong", "merchant@bakong", Instant.now());

        assertTrue(service.verifyForAdmin(1L).verified());

        verify(orders).markPaid(1L);
        verify(bakong, never()).checkTransaction(MD5);
    }

    @Test
    void customerCannotVerifyAnotherCustomersPayment() {
        when(orders.requireOrder(1L)).thenThrow(new ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN, "Access denied."));

        var error = assertThrows(ResponseStatusException.class, () -> service.verify(1L));

        assertEquals(403, error.getStatusCode().value());
        verify(orders, never()).requirePaymentContext(1L);
        verify(bakong, never()).checkTransaction(MD5);
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void keepsVerifiedPaymentAndRetriesOrderUpdateWithoutCallingBakongAgain(CapturedOutput output) {
        when(bakong.checkTransaction(MD5)).thenReturn(new BakongResponse(0, null, "OK",
                new Transaction("hash", "payer@bakong", "merchant@bakong", "KHR", new BigDecimal("40"), null)));
        doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE))
                .doNothing()
                .when(orders).markPaid(1L);

        assertTrue(service.verify(1L).verified());
        assertEquals(PaymentStatus.VERIFIED, qr.getStatus());
        assertTrue(qr.getPaidAt() != null);
        assertEquals("ORDER_SYNC_FAILED", qr.getFailureReason());
        assertTrue(service.verify(1L).verified());

        verify(qrRepository, times(3)).save(qr);
        verify(orders, times(2)).markPaid(1L);
        verify(bakong, times(1)).checkTransaction(MD5);
        assertEquals(null, qr.getFailureReason());
        assertTrue(output.getAll().contains(
                "Payment verified but order update failed. paymentId=1, orderId=1"));
    }
}
