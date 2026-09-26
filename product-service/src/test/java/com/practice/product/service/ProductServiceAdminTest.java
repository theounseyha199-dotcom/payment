package com.practice.product.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.practice.product.dto.UpdateProductRequest;
import com.practice.product.entity.Product;
import com.practice.product.entity.ProductStatus;
import com.practice.product.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class ProductServiceAdminTest {
    private final ProductRepository repository = mock(ProductRepository.class);
    private final ProductService service = new ProductService(repository);

    @Test
    void adminListUsesPaginationAndStatusSpecification() {
        Product product = new Product("Notebook", new BigDecimal("5500"));
        when(repository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(java.util.List.of(product), PageRequest.of(0, 20), 1));
        var result = service.adminList("note", ProductStatus.ACTIVE, PageRequest.of(0, 20));
        assertEquals(1, result.totalElements());
        assertTrue(result.first());
    }

    @Test
    void adminCanEditAndChangeStatus() {
        Product product = new Product("Notebook", new BigDecimal("5500"));
        when(repository.findById(1L)).thenReturn(Optional.of(product));
        when(repository.save(product)).thenReturn(product);
        var updated = service.adminUpdate(1L, new UpdateProductRequest("Notebook Pro", new BigDecimal("6500")));
        assertEquals("Notebook Pro", updated.name());
        assertEquals(new BigDecimal("6500"), updated.price());
        assertEquals(ProductStatus.INACTIVE, service.adminUpdateStatus(1L, ProductStatus.INACTIVE).status());
    }

    @Test
    void emptyPatchIsRejected() {
        var error = assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.adminUpdate(1L, new UpdateProductRequest(null, null)));
        assertEquals(400, error.getStatusCode().value());
        verifyNoInteractions(repository);
    }

    @Test
    void statsCountActiveAndInactiveProducts() {
        when(repository.countByStatus(ProductStatus.ACTIVE)).thenReturn(3L);
        when(repository.countByStatus(ProductStatus.INACTIVE)).thenReturn(2L);
        when(repository.countByStatusIsNull()).thenReturn(1L);
        var stats = service.adminStats();
        assertEquals(6, stats.total());
        assertEquals(4, stats.active());
        assertEquals(2, stats.inactive());
    }
}
