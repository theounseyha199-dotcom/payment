package com.practice.order.exception;

import java.util.Map;

public record ApiError(String status, String message, Map<String, String> details) {
    public static ApiError of(String message) {
        return new ApiError("error", message, Map.of());
    }
}
