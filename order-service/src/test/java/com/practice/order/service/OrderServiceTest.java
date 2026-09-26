package com.practice.order.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.practice.order.client.CatalogClient;
import com.practice.order.client.CatalogClient.ProductSummary;
import com.practice.order.dto.CreateOrderRequest;
import com.practice.order.entity.PurchaseOrder;
import com.practice.order.entity.OrderStatus;
import com.practice.order.repository.OrderRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

class OrderServiceTest {
    @Test
    void createsOrderUsingProductPrice() {
        OrderRepository orders = Mockito.mock(OrderRepository.class);
        CatalogClient catalog = Mockito.mock(CatalogClient.class);
        when(catalog.requireProduct(2L))
                .thenReturn(new ProductSummary(2L, "Notebook", new BigDecimal("100")));
        when(orders.save(any(PurchaseOrder.class))).thenAnswer(call -> call.getArgument(0));

        var result = new OrderService(orders, catalog)
                .create(new CreateOrderRequest(1L, 2L, 3));

        verify(catalog).requireUser(1L);
        assertEquals(new BigDecimal("100"), result.unitPrice());
        assertEquals(new BigDecimal("300"), result.totalAmount());
        assertEquals("KHR", result.currency());
        assertEquals(OrderStatus.PENDING_PAYMENT, result.status());
        assertEquals(3, result.quantity());
    }

    @Test
    void rejectsTotalTooLargeForDatabaseColumn() {
        OrderRepository orders = Mockito.mock(OrderRepository.class);
        CatalogClient catalog = Mockito.mock(CatalogClient.class);
        when(catalog.requireProduct(2L))
                .thenReturn(new ProductSummary(2L, "Large item", new BigDecimal("9999999999")));

        var error = assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> new OrderService(orders, catalog).create(new CreateOrderRequest(1L, 2L, 2)));

        assertEquals(400, error.getStatusCode().value());
        Mockito.verify(orders, Mockito.never()).save(any(PurchaseOrder.class));
    }

    @Test
    void marksPendingOrderPaidAndAcceptsRepeatedPaymentUpdate() {
        OrderRepository orders = Mockito.mock(OrderRepository.class);
        PurchaseOrder order = new PurchaseOrder(1L, 2L, 2, new BigDecimal("5500"));
        when(orders.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(orders.save(order)).thenReturn(order);
        OrderService service = new OrderService(orders, Mockito.mock(CatalogClient.class));

        assertEquals(OrderStatus.PAID, service.updatePaymentStatus(1L, OrderStatus.PAID).status());
        assertEquals(OrderStatus.PAID, service.updatePaymentStatus(1L, OrderStatus.PAID).status());
        verify(orders, Mockito.times(1)).save(order);
    }

    @Test
    void rejectsPaymentUpdateToAnotherStatus() {
        OrderRepository orders = Mockito.mock(OrderRepository.class);
        OrderService service = new OrderService(orders, Mockito.mock(CatalogClient.class));

        var error = assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.updatePaymentStatus(1L, OrderStatus.CANCELLED));

        assertEquals(400, error.getStatusCode().value());
        Mockito.verifyNoInteractions(orders);
    }

    @Test
    void rejectsPaymentForCancelledOrder() {
        OrderRepository orders = Mockito.mock(OrderRepository.class);
        PurchaseOrder order = new PurchaseOrder(1L, 2L, 1, new BigDecimal("100"));
        ReflectionTestUtils.setField(order, "status", OrderStatus.CANCELLED);
        when(orders.findByIdForUpdate(1L)).thenReturn(Optional.of(order));

        var error = assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> new OrderService(orders, Mockito.mock(CatalogClient.class))
                        .updatePaymentStatus(1L, OrderStatus.PAID));

        assertEquals(409, error.getStatusCode().value());
        Mockito.verify(orders, Mockito.never()).save(any(PurchaseOrder.class));
    }

    @Test
    void rejectsOrderWhenUserDoesNotExist() {
        OrderRepository orders = Mockito.mock(OrderRepository.class);
        CatalogClient catalog = Mockito.mock(CatalogClient.class);
        Mockito.doThrow(new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST, "User does not exist"))
                .when(catalog).requireUser(1L);

        var error = assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> new OrderService(orders, catalog).create(new CreateOrderRequest(1L, 2L, 1)));

        assertEquals(400, error.getStatusCode().value());
        Mockito.verify(catalog, Mockito.never()).requireProduct(2L);
        Mockito.verify(orders, Mockito.never()).save(any(PurchaseOrder.class));
    }

    @Test
    void rejectsOrderWhenProductDoesNotExist() {
        OrderRepository orders = Mockito.mock(OrderRepository.class);
        CatalogClient catalog = Mockito.mock(CatalogClient.class);
        when(catalog.requireProduct(2L)).thenThrow(new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST, "Product does not exist"));

        var error = assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> new OrderService(orders, catalog).create(new CreateOrderRequest(1L, 2L, 1)));

        assertEquals(400, error.getStatusCode().value());
        verify(catalog).requireUser(1L);
        Mockito.verify(orders, Mockito.never()).save(any(PurchaseOrder.class));
    }

    @Test
    void missingOrderReturns404ForPaymentUpdate() {
        OrderRepository orders = Mockito.mock(OrderRepository.class);
        when(orders.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        var error = assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> new OrderService(orders, Mockito.mock(CatalogClient.class))
                        .updatePaymentStatus(99L, OrderStatus.PAID));

        assertEquals(404, error.getStatusCode().value());
    }
}
