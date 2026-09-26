package com.practice.payment.controller;

import com.practice.payment.dto.PaymentVerificationResponse;
import com.practice.payment.dto.PaymentResponse;
import com.practice.payment.dto.ApiResult;
import com.practice.payment.dto.CreateQrRequest;
import com.practice.payment.dto.QrPaymentResponse;
import com.practice.payment.dto.VerifyPaymentRequest;
import com.practice.payment.service.PaymentService;
import com.practice.payment.service.QrPaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/payments")
public class PaymentController {
    private final PaymentService payments;
    private final QrPaymentService qrPayments;

    public PaymentController(PaymentService payments, QrPaymentService qrPayments) {
        this.payments = payments;
        this.qrPayments = qrPayments;
    }

    @PostMapping("/qr")
    @Operation(summary = "Generate a KHR Bakong KHQR for an order")
    public ApiResult<QrPaymentResponse> createQr(@Valid @RequestBody CreateQrRequest request) {
        return ApiResult.success("KHQR generated.",
                qrPayments.create(request.orderId()));
    }

    @GetMapping("/{paymentId}")
    @Operation(summary = "Get a payment by ID")
    public ApiResult<PaymentResponse> get(@PathVariable Long paymentId) {
        return ApiResult.success("Payment found.", payments.get(paymentId));
    }

    @PostMapping("/{paymentId}/verify")
    @Operation(summary = "Verify a stored payment with Bakong")
    public ApiResult<PaymentVerificationResponse> verify(@PathVariable Long paymentId) {
        return verificationResult(payments.verify(paymentId));
    }

    @Deprecated
    @Hidden
    @PostMapping("/verify")
    public ApiResult<PaymentVerificationResponse> verifyLegacy(
            @Valid @RequestBody VerifyPaymentRequest request) {
        return verificationResult(payments.verify(request));
    }

    private ApiResult<PaymentVerificationResponse> verificationResult(PaymentVerificationResponse result) {
        String message = switch (result.status()) {
            case "VERIFIED" -> "Payment verified for this order.";
            case "MISMATCH" -> "Payment found, but its details do not match this order.";
            case "EXPIRED" -> "The QR has expired without a confirmed payment.";
            default -> "Payment has not been confirmed by Bakong yet.";
        };
        return ApiResult.success(message, result);
    }
}
