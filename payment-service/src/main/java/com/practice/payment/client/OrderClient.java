package com.practice.payment.client;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Component
public class OrderClient {
    private final RestClient client;

    public OrderClient(@Qualifier("loadBalancedRestClientBuilder") RestClient.Builder builder) {
        this.client = builder.build();
    }

    public OrderSummary requireOrder(Long orderId) {
        try {
            OrderEnvelope response = client.get().uri("http://order-service/orders/{id}", orderId)
                    .retrieve().body(OrderEnvelope.class);
            OrderSummary order = response == null ? null : response.data() != null
                    ? response.data() : new OrderSummary(response.id(), response.total());
            if (order == null || order.id() == null || order.total() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Order details are temporarily unavailable.");
            }
            return order;
        } catch (HttpClientErrorException.NotFound exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Order service unavailable", exception);
        }
    }

    public void markPaid(Long orderId) {
        try {
            client.patch().uri("http://order-service/internal/orders/{id}/payment", orderId)
                    .body(new PaymentUpdate("PAID"))
                    .retrieve().toBodilessEntity();
        } catch (HttpClientErrorException.NotFound exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found", exception);
        } catch (HttpClientErrorException.Conflict exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Order cannot be paid in its current status.", exception);
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Order payment update is temporarily unavailable.", exception);
        }
    }

    public record OrderSummary(Long id, @JsonAlias("totalAmount") BigDecimal total, String status) {
        public OrderSummary(Long id, BigDecimal total) {
            this(id, total, null);
        }
    }
    private record OrderEnvelope(String status, String message, OrderSummary data,
                                 Long id, @JsonAlias("totalAmount") BigDecimal total) { }
    private record PaymentUpdate(String status) { }
}
