package com.practice.product.service;

import com.practice.product.dto.CreateProductRequest;
import com.practice.product.dto.ProductResponse;
import com.practice.product.entity.Product;
import com.practice.product.repository.ProductRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProductService {
    private final ProductRepository products;

    public ProductService(ProductRepository products) {
        this.products = products;
    }

    public ProductResponse create(CreateProductRequest request) {
        return ProductResponse.from(products.save(new Product(request.name(), request.price())));
    }

    public ProductResponse get(Long id) {
        Product product = products.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
        return ProductResponse.from(product);
    }

    public List<ProductResponse> list() {
        return products.findAll().stream().map(ProductResponse::from).toList();
    }
}
