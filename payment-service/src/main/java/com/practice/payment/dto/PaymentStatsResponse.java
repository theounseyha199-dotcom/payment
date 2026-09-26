package com.practice.payment.dto;

import java.math.BigDecimal;

public record PaymentStatsResponse(long total, long pending, long verified, long unconfirmed,
                                   long mismatch, long expired, BigDecimal revenueToday,
                                   BigDecimal revenueThisWeek) { }
