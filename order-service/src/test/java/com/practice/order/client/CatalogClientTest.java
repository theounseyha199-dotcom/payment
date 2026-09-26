package com.practice.order.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

class CatalogClientTest {
    @Test
    void readsProductFromFriendlyResponse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://product-service/products/2"))
                .andRespond(withSuccess("""
                        {"status":"success","message":"Product found.",
                         "data":{"id":2,"name":"Notebook","price":5.50}}
                        """, MediaType.APPLICATION_JSON));

        var product = new CatalogClient(builder).requireProduct(2L);

        assertEquals(2L, product.id());
        assertEquals(new BigDecimal("5.50"), product.price());
        server.verify();
    }

    @Test
    void readsProductFromOlderDirectResponse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://product-service/products/4"))
                .andRespond(withSuccess("""
                        {"id":4,"name":"KHQR scan test","price":40}
                        """, MediaType.APPLICATION_JSON));

        var product = new CatalogClient(builder).requireProduct(4L);

        assertEquals(4L, product.id());
        assertEquals(new BigDecimal("40"), product.price());
        server.verify();
    }

    @Test
    void rejectsInactiveProductForOrderCreation() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://product-service/products/5"))
                .andRespond(withSuccess("""
                        {"status":"success","data":{"id":5,"name":"Notebook",
                         "price":5500,"status":"INACTIVE"}}
                        """, MediaType.APPLICATION_JSON));

        var error = assertThrows(ResponseStatusException.class,
                () -> new CatalogClient(builder).requireProduct(5L));

        assertEquals(409, error.getStatusCode().value());
        assertEquals("Product is not available.", error.getReason());
        server.verify();
    }
}
