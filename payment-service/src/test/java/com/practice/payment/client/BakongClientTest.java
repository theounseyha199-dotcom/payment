package com.practice.payment.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class BakongClientTest {
    @Test
    void missingTokenReturns503WithoutSendingRequest() {
        BakongClient client = new BakongClient("http://127.0.0.1:1", "");

        var error = assertThrows(ResponseStatusException.class,
                () -> client.checkTransaction("0123456789abcdef0123456789abcdef"));

        assertEquals(503, error.getStatusCode().value());
        assertEquals("Bakong configuration is unavailable.", error.getReason());
    }

    @Test
    void sendsStoredMd5ToConfiguredBaseUrlWithBearerToken() throws Exception {
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> contentType = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/check_transaction_by_md5", exchange -> {
            method.set(exchange.getRequestMethod());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"responseCode\":1,\"data\":null}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (var output = exchange.getResponseBody()) {
                output.write(response);
            }
        });
        server.start();
        try {
            BakongClient client = new BakongClient("http://127.0.0.1:" + server.getAddress().getPort(),
                    "test-token");

            client.checkTransaction("0123456789abcdef0123456789abcdef");

            assertEquals("POST", method.get());
            assertEquals("Bearer test-token", authorization.get());
            assertEquals("application/json", contentType.get());
            assertEquals("{\"md5\":\"0123456789abcdef0123456789abcdef\"}", body.get());
        } finally {
            server.stop(0);
        }
    }
}
