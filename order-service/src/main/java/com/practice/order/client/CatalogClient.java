package com.practice.order.client;

import java.math.BigDecimal;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Component
public class CatalogClient {
    private final RestClient client;

    public CatalogClient(@Qualifier("loadBalancedRestClientBuilder") RestClient.Builder builder) {
        this.client = builder.build();
    }

    public void requireUser(Long userId) {
        try {
            client.get().uri("http://user-service/users/{id}", userId)
                    .retrieve().toBodilessEntity();
        } catch (HttpClientErrorException.NotFound exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User does not exist");
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "User service unavailable", exception);
        }
    }

    public ProductSummary requireProduct(Long productId) {
        try {
            ProductEnvelope response = client.get().uri("http://product-service/products/{id}", productId)
                    .retrieve().body(ProductEnvelope.class);
            ProductSummary product = response == null ? null : response.data() != null
                    ? response.data() : new ProductSummary(response.id(), response.name(), response.price());
            if (product == null || product.id() == null || product.price() == null) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Product details are temporarily unavailable.");
            }
            return product;
        } catch (HttpClientErrorException.NotFound exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product does not exist");
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Product service unavailable", exception);
        }
    }

    public record ProductSummary(Long id, String name, BigDecimal price) { }
    private record ProductEnvelope(String status, String message, ProductSummary data,
                                   Long id, String name, BigDecimal price) { }
}
