package com.practice.payment.client;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Component
public class OrderClient {
    private final RestClient client;
    private final String internalToken;

    @Autowired
    public OrderClient(@Qualifier("loadBalancedRestClientBuilder") RestClient.Builder builder,
                       @Value("${internal.service-token:}") String internalToken) {
        this.client = builder.build();
        this.internalToken = internalToken;
    }

    public OrderClient(RestClient.Builder builder) {
        this(builder, "");
    }

    public OrderSummary requireOrder(Long orderId) {
        try {
            OrderEnvelope response = client.get().uri("http://order-service/orders/{id}", orderId)
                    .headers(headers -> {
                        if (SecurityContextHolder.getContext().getAuthentication()
                                instanceof JwtAuthenticationToken token) {
                            headers.setBearerAuth(token.getToken().getTokenValue());
                        }
                    })
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
                    .header("X-Internal-Service-Token", internalToken)
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
