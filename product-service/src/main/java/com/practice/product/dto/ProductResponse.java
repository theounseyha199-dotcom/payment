package com.practice.product.dto;

import com.practice.product.entity.Product;
import com.practice.product.entity.ProductStatus;
import java.math.BigDecimal;

public record ProductResponse(Long id, String name, BigDecimal price, ProductStatus status) {
    public static ProductResponse from(Product product) {
        return new ProductResponse(product.getId(), product.getName(), product.getPrice(), product.getStatus());
    }
}
