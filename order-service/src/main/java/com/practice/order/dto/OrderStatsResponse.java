package com.practice.order.dto;

public record OrderStatsResponse(long total, long today, long pendingPayment, long paid, long cancelled) { }
