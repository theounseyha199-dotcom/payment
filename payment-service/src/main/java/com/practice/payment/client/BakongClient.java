package com.practice.payment.client;

import java.math.BigDecimal;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Component
public class BakongClient {
    private final RestClient client;
    private final String token;

    public BakongClient(@Value("${bakong.base-url}") String baseUrl,
                        @Value("${bakong.token}") String token) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
        this.token = token;
    }

    public BakongResponse checkTransaction(String md5) {
        if (!StringUtils.hasText(token)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Bakong configuration is unavailable.");
        }
        try {
            BakongResponse response = client.post().uri("/v1/check_transaction_by_md5")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + token)
                    .body(new CheckRequest(md5))
                    .retrieve().body(BakongResponse.class);
            if (response == null || response.responseCode() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The payment provider returned an invalid response.");
            }
            return response;
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The payment provider is temporarily unavailable.", exception);
        }
    }

    private record CheckRequest(String md5) { }
    public record BakongResponse(Integer responseCode, Integer errorCode, String responseMessage,
                                 Transaction data) { }
    public record Transaction(String hash, String fromAccountId, String toAccountId,
                              String currency, BigDecimal amount, String description) { }
}
