package com.practice.payment.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.practice.payment.client.OrderClient;
import com.practice.payment.client.OrderClient.OrderSummary;
import com.practice.payment.dto.QrPaymentResponse;
import com.practice.payment.entity.PaymentQr;
import com.practice.payment.entity.PaymentStatus;
import com.practice.payment.repository.PaymentQrRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.EnumSet;
import java.util.Map;
import kh.gov.nbc.bakong_khqr.BakongKHQR;
import kh.gov.nbc.bakong_khqr.model.IndividualInfo;
import kh.gov.nbc.bakong_khqr.model.KHQRCurrency;
import kh.gov.nbc.bakong_khqr.model.KHQRData;
import kh.gov.nbc.bakong_khqr.model.KHQRResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class QrPaymentService {
    private static final Duration QR_LIFETIME = Duration.ofMinutes(5);
    private static final EnumSet<PaymentStatus> ACTIVE_STATUSES = EnumSet.of(
            PaymentStatus.PENDING, PaymentStatus.UNCONFIRMED);

    private final OrderClient orders;
    private final PaymentQrRepository qrRepository;
    private final String accountId;
    private final String merchantName;
    private final String merchantCity;
    private final String acquiringBank;
    private final String accountInformation;
    private final String currency;

    public QrPaymentService(OrderClient orders, PaymentQrRepository qrRepository,
                            @Value("${bakong.account-id}") String accountId,
                            @Value("${bakong.merchant-name}") String merchantName,
                            @Value("${bakong.merchant-city}") String merchantCity,
                            @Value("${bakong.acquiring-bank}") String acquiringBank,
                            @Value("${bakong.account-information}") String accountInformation,
                            @Value("${bakong.currency}") String currency) {
        this.orders = orders;
        this.qrRepository = qrRepository;
        this.accountId = accountId;
        this.merchantName = merchantName;
        this.merchantCity = merchantCity;
        this.acquiringBank = acquiringBank;
        this.accountInformation = accountInformation;
        this.currency = currency;
    }

    public QrPaymentResponse create(Long orderId) {
        OrderSummary order = orders.requireOrder(orderId);
        if ("PAID".equals(order.status())
                || qrRepository.existsByOrderIdAndStatus(orderId, PaymentStatus.VERIFIED)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order is already paid.");
        }
        if (order.status() != null && !"PENDING_PAYMENT".equals(order.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only an order awaiting payment can receive a QR.");
        }
        Instant now = Instant.now();
        var previous = qrRepository.findFirstByOrderIdAndStatusInOrderByCreatedAtDesc(
                orderId, ACTIVE_STATUSES);
        if (previous.isPresent()) {
            PaymentQr payment = previous.get();
            if (payment.getQr() != null && now.isBefore(payment.getExpiresAt())) {
                return responseFor(payment);
            }
            payment.markExpired();
            qrRepository.save(payment);
        }
        if (!StringUtils.hasText(accountId) || !StringUtils.hasText(merchantName)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Bakong configuration is unavailable.");
        }
        if (!"KHR".equalsIgnoreCase(currency)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "KHR QR payments require KHR payment configuration.");
        }
        BigDecimal amount = order.total();
        if (amount.signum() <= 0 || amount.remainder(BigDecimal.ONE).signum() != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The order total must be a positive whole KHR amount.");
        }

        Instant expiresAt = now.plus(QR_LIFETIME);
        IndividualInfo info = new IndividualInfo();
        info.setBakongAccountId(accountId);
        info.setMerchantName(merchantName);
        if (StringUtils.hasText(merchantCity)) {
            info.setMerchantCity(merchantCity);
        }
        if (StringUtils.hasText(acquiringBank)) {
            info.setAcquiringBank(acquiringBank);
        }
        if (StringUtils.hasText(accountInformation)) {
            info.setAccountInformation(accountInformation);
        }
        info.setCurrency(KHQRCurrency.KHR);
        info.setAmount(amount.doubleValue());
        info.setBillNumber("ORDER-" + order.id());
        info.setExpirationTimestamp(expiresAt.toEpochMilli());

        KHQRResponse<KHQRData> result = BakongKHQR.generateIndividual(info);
        if (result == null || result.getKHQRStatus() == null
                || result.getKHQRStatus().getCode() != 0 || result.getData() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Unable to generate a Bakong QR with the configured account details.");
        }
        KHQRData data = result.getData();
        if (!StringUtils.hasText(data.getQr()) || !StringUtils.hasText(data.getMd5())) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Bakong returned an incomplete QR.");
        }
        String image = renderPngDataUrl(data.getQr());
        try {
            PaymentQr saved = qrRepository.save(new PaymentQr(data.getMd5(), order.id(), data.getQr(),
                    amount, "KHR", accountId, expiresAt));
            return responseFor(saved, image);
        } catch (DataIntegrityViolationException exception) {
            // Another request may have saved the active QR first.
            return qrRepository.findFirstByOrderIdAndStatusInOrderByCreatedAtDesc(orderId, ACTIVE_STATUSES)
                    .filter(payment -> payment.getQr() != null && Instant.now().isBefore(payment.getExpiresAt()))
                    .map(this::responseFor)
                    .orElseThrow(() -> exception);
        }
    }

    private QrPaymentResponse responseFor(PaymentQr payment) {
        return responseFor(payment, renderPngDataUrl(payment.getQr()));
    }

    private QrPaymentResponse responseFor(PaymentQr payment, String image) {
        return new QrPaymentResponse(payment.getId(), payment.getOrderId(), payment.getAmount(),
                payment.getCurrency(), payment.getQr(), payment.getMd5(), image,
                payment.getStatus(), payment.getExpiresAt());
    }

    private String renderPngDataUrl(String qr) {
        try {
            var matrix = new MultiFormatWriter().encode(qr, BarcodeFormat.QR_CODE, 400, 400,
                    Map.of(EncodeHintType.CHARACTER_SET, "UTF-8", EncodeHintType.MARGIN, 2));
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", output);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (WriterException | IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to create the QR image.", exception);
        }
    }
}
