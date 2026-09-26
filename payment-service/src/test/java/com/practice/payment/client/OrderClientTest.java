package com.practice.payment.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OrderClientTest {
    @Test
    void readsOrderFromFriendlyResponse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://order-service/orders/3"))
                .andRespond(withSuccess("""
                        {"status":"success","message":"Order found.",
                         "data":{"id":3,"unitPrice":7.25,"totalAmount":21.75,
                                 "currency":"KHR","status":"PENDING_PAYMENT"}}
                        """, MediaType.APPLICATION_JSON));

        var order = new OrderClient(builder).requireOrder(3L);

        assertEquals(3L, order.id());
        assertEquals(new BigDecimal("21.75"), order.total());
        assertEquals("PENDING_PAYMENT", order.status());
        server.verify();
    }

    @Test
    void readsOrderFromOlderDirectResponse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://order-service/orders/4"))
                .andRespond(withSuccess("""
                        {"id":4,"total":40}
                        """, MediaType.APPLICATION_JSON));

        var order = new OrderClient(builder).requireOrder(4L);

        assertEquals(4L, order.id());
        assertEquals(new BigDecimal("40"), order.total());
        server.verify();
    }

    @Test
    void marksOrderPaidThroughInternalEndpoint() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://order-service/internal/orders/3/payment"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(content().json("{\"status\":\"PAID\"}"))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        new OrderClient(builder).markPaid(3L);

        server.verify();
    }
}
