package com.practice.user.dto;

public record ApiResult<T>(String status, String message, T data) {
    public static <T> ApiResult<T> success(String message, T data) {
        return new ApiResult<>("success", message, data);
    }
}
