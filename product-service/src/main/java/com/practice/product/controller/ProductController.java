package com.practice.product.controller;

import com.practice.product.dto.CreateProductRequest;
import com.practice.product.dto.ApiResult;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import com.practice.product.dto.ProductResponse;
import com.practice.product.service.ProductService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/products")
public class ProductController {
    private final ProductService products;

    public ProductController(ProductService products) {
        this.products = products;
    }

    @PostMapping
    @ApiResponse(responseCode = "201", description = "Product created")
    public ResponseEntity<ApiResult<ProductResponse>> create(@Valid @RequestBody CreateProductRequest request) {
        ProductResponse product = products.create(request);
        return ResponseEntity.created(URI.create("/products/" + product.id()))
                .body(ApiResult.success("Product created successfully.", product));
    }

    @GetMapping("/{id}")
    public ApiResult<ProductResponse> get(@PathVariable Long id) {
        return ApiResult.success("Product found.", products.get(id));
    }

    @GetMapping
    public ApiResult<List<ProductResponse>> list() {
        return ApiResult.success("Products loaded successfully.", products.list());
    }
}
