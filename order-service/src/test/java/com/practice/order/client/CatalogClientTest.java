package com.practice.order.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

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
}
