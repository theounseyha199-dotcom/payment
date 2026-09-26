package com.practice.payment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.practice.payment.client.OrderClient;
import com.practice.payment.client.OrderClient.OrderSummary;
import com.practice.payment.entity.PaymentQr;
import com.practice.payment.entity.PaymentStatus;
import com.practice.payment.repository.PaymentQrRepository;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import javax.imageio.ImageIO;
import kh.gov.nbc.bakong_khqr.BakongKHQR;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class QrPaymentServiceTest {
    private final OrderClient orders = Mockito.mock(OrderClient.class);
    private final PaymentQrRepository qrRepository = Mockito.mock(PaymentQrRepository.class);
    private final QrPaymentService service = new QrPaymentService(
            orders, qrRepository, "test@devb", "Test Merchant", "PHNOM PENH", "", "", "KHR");

    @BeforeEach
    void setUp() {
        Mockito.when(qrRepository.save(Mockito.any())).thenAnswer(call -> {
            var payment = (com.practice.payment.entity.PaymentQr) call.getArgument(0);
            ReflectionTestUtils.setField(payment, "id", 1L);
            return payment;
        });
    }

    @Test
    void generatesValidQrForOrderAmount() throws Exception {
        when(orders.requireOrder(7L)).thenReturn(new OrderSummary(7L, new BigDecimal("100")));

        var qr = service.create(7L);

        assertEquals(7L, qr.orderId());
        assertEquals(1L, qr.paymentId());
        assertEquals(new BigDecimal("100"), qr.amount());
        assertEquals("KHR", qr.currency());
        assertEquals(com.practice.payment.entity.PaymentStatus.PENDING, qr.paymentStatus());
        assertTrue(qr.qr().startsWith("000201010212"));
        assertEquals(32, qr.md5().length());
        assertTrue(qr.imageDataUrl().startsWith("data:image/png;base64,"));
        assertFalse(qr.expiresAt().isBefore(java.time.Instant.now()));
        assertTrue(BakongKHQR.verify(qr.qr()).getData().isValid());
        byte[] imageBytes = Base64.getDecoder().decode(qr.imageDataUrl().split(",", 2)[1]);
        var image = ImageIO.read(new ByteArrayInputStream(imageBytes));
        var bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)));
        assertEquals(qr.qr(), new MultiFormatReader().decode(bitmap).getText());
        String expectedMd5 = HexFormat.of().formatHex(
                MessageDigest.getInstance("MD5").digest(qr.qr().getBytes(StandardCharsets.UTF_8)));
        assertEquals(expectedMd5, qr.md5());
        Mockito.verify(qrRepository).save(Mockito.argThat(saved ->
                saved.getOrderId().equals(7L) && saved.getMd5().equals(qr.md5())
                        && saved.getQr().equals(qr.qr())
                        && saved.getReceivingAccountId().equals("test@devb")));
    }

    @Test
    void rejectsFractionalRielOrders() {
        when(orders.requireOrder(8L)).thenReturn(new OrderSummary(8L, new BigDecimal("11.25")));

        var error = assertThrows(ResponseStatusException.class, () -> service.create(8L));

        assertEquals(400, error.getStatusCode().value());
    }

    @Test
    void rejectsQrForPaidOrder() {
        when(orders.requireOrder(8L)).thenReturn(new OrderSummary(8L, new BigDecimal("100"), "PAID"));

        QrPaymentService unconfigured = new QrPaymentService(
                orders, qrRepository, "", "", "PHNOM PENH", "", "", "KHR");
        var error = assertThrows(ResponseStatusException.class, () -> unconfigured.create(8L));

        assertEquals(409, error.getStatusCode().value());
        assertEquals("Order is already paid.", error.getReason());
    }

    @Test
    void returnsExistingActiveQrWithoutSavingAnother() {
        when(orders.requireOrder(7L)).thenReturn(new OrderSummary(7L, new BigDecimal("100"), "PENDING_PAYMENT"));
        PaymentQr existing = new PaymentQr("0123456789abcdef0123456789abcdef", 7L,
                "existing-qr", new BigDecimal("100"), "KHR", "test@devb",
                Instant.now().plusSeconds(180));
        ReflectionTestUtils.setField(existing, "id", 22L);
        when(qrRepository.findFirstByOrderIdAndStatusInOrderByCreatedAtDesc(
                Mockito.eq(7L), Mockito.anyCollection())).thenReturn(Optional.of(existing));

        QrPaymentService unconfigured = new QrPaymentService(
                orders, qrRepository, "", "", "PHNOM PENH", "", "", "KHR");
        var response = unconfigured.create(7L);

        assertEquals(22L, response.paymentId());
        assertEquals("existing-qr", response.qr());
        assertEquals(PaymentStatus.PENDING, response.paymentStatus());
        Mockito.verify(qrRepository, Mockito.never()).save(Mockito.any());
    }

    @Test
    void expiresOldQrAndCreatesNewOne() {
        when(orders.requireOrder(7L)).thenReturn(new OrderSummary(7L, new BigDecimal("100"), "PENDING_PAYMENT"));
        PaymentQr existing = new PaymentQr("0123456789abcdef0123456789abcdef", 7L,
                "expired-qr", new BigDecimal("100"), "KHR", "test@devb",
                Instant.now().minusSeconds(1));
        ReflectionTestUtils.setField(existing, "id", 22L);
        when(qrRepository.findFirstByOrderIdAndStatusInOrderByCreatedAtDesc(
                Mockito.eq(7L), Mockito.anyCollection())).thenReturn(Optional.of(existing));

        var response = service.create(7L);

        assertEquals(PaymentStatus.EXPIRED, existing.getStatus());
        assertEquals(1L, response.paymentId());
        Mockito.verify(qrRepository).save(existing);
        Mockito.verify(qrRepository, Mockito.times(2)).save(Mockito.any());
    }

    @Test
    void mismatchDoesNotCountAsReusableQr() {
        when(orders.requireOrder(7L)).thenReturn(new OrderSummary(7L,
                new BigDecimal("100"), "PENDING_PAYMENT"));

        var response = service.create(7L);

        assertEquals(PaymentStatus.PENDING, response.paymentStatus());
        Mockito.verify(qrRepository).findFirstByOrderIdAndStatusInOrderByCreatedAtDesc(
                Mockito.eq(7L), Mockito.argThat(statuses ->
                        statuses.contains(PaymentStatus.PENDING)
                                && statuses.contains(PaymentStatus.UNCONFIRMED)
                                && !statuses.contains(PaymentStatus.MISMATCH)));
    }

    @Test
    void usesAnotherOrderTotalInsteadOfAConfiguredTestAmount() {
        when(orders.requireOrder(9L)).thenReturn(new OrderSummary(9L, new BigDecimal("22308")));

        var qr = service.create(9L);

        assertEquals(new BigDecimal("22308"), qr.amount());
        assertEquals("KHR", qr.currency());
    }

    @Test
    void missingOrderPreventsQrCreation() {
        when(orders.requireOrder(99L)).thenThrow(new ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND, "Order not found"));

        var error = assertThrows(ResponseStatusException.class, () -> service.create(99L));

        assertEquals(404, error.getStatusCode().value());
        Mockito.verify(qrRepository, Mockito.never()).save(Mockito.any());
    }

    @Test
    void missingBakongAccountReturns503() {
        when(orders.requireOrder(7L)).thenReturn(new OrderSummary(7L, new BigDecimal("100")));
        QrPaymentService unconfigured = new QrPaymentService(
                orders, qrRepository, "", "Test Merchant", "PHNOM PENH", "", "", "KHR");

        var error = assertThrows(ResponseStatusException.class, () -> unconfigured.create(7L));

        assertEquals(503, error.getStatusCode().value());
        assertEquals("Bakong configuration is unavailable.", error.getReason());
    }

    @Test
    void missingMerchantNameReturns503() {
        when(orders.requireOrder(7L)).thenReturn(new OrderSummary(7L, new BigDecimal("100")));
        QrPaymentService unconfigured = new QrPaymentService(
                orders, qrRepository, "test@devb", "", "PHNOM PENH", "", "", "KHR");

        var error = assertThrows(ResponseStatusException.class, () -> unconfigured.create(7L));

        assertEquals(503, error.getStatusCode().value());
        assertEquals("Bakong configuration is unavailable.", error.getReason());
    }
}
