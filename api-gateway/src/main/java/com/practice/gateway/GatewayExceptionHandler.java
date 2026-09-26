package com.practice.gateway;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpServerErrorException;

@RestControllerAdvice
public class GatewayExceptionHandler {
    @ExceptionHandler(HttpServerErrorException.class)
    public ResponseEntity<GatewayError> handleServiceFailure(HttpServerErrorException exception) {
        if (exception.getStatusCode().value() == 503) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new GatewayError("error", "This service is temporarily unavailable. Please try again shortly.", Map.of()));
        }
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new GatewayError("error", "The service could not complete your request. Please try again later.", Map.of()));
    }

    public record GatewayError(String status, String message, Map<String, String> details) { }
}
