package com.practice.product.controller;

import com.practice.product.dto.ApiResult;
import com.practice.product.dto.CreateProductRequest;
import com.practice.product.dto.ProductResponse;
import com.practice.product.dto.PageResponse;
import com.practice.product.dto.ProductStatsResponse;
import com.practice.product.dto.UpdateProductRequest;
import com.practice.product.dto.UpdateProductStatusRequest;
import com.practice.product.entity.ProductStatus;
import com.practice.product.service.ProductService;
import com.practice.product.service.AdminAuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestController
@RequestMapping("/admin/products")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Products", description = "Administrator product management APIs")
public class AdminProductController {
    private final ProductService products;
    private final AdminAuditService audit;

    public AdminProductController(ProductService products, AdminAuditService audit) {
        this.products = products; this.audit = audit;
    }

    @GetMapping
    @Operation(summary = "List products for administrators")
    public ApiResult<PageResponse<ProductResponse>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id,desc") String sort) {
        return ApiResult.success("Products loaded successfully.", products.adminList(search, status, pageable(page, size, sort)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a product for administrators")
    public ApiResult<ProductResponse> get(@PathVariable Long id) {
        return ApiResult.success("Product found.", products.get(id));
    }

    @PostMapping
    @Operation(summary = "Create a product as an administrator")
    public ResponseEntity<ApiResult<ProductResponse>> create(@Valid @RequestBody CreateProductRequest request,
                                                              @AuthenticationPrincipal Jwt jwt) {
        ProductResponse product = products.create(request);
        audit.record(jwt.getSubject(), "PRODUCT_CREATED", product.id(), null,
                Map.of("name", product.name(), "price", product.price()));
        return ResponseEntity.created(URI.create("/admin/products/" + product.id()))
                .body(ApiResult.success("Product created successfully.", product));
    }

    @PatchMapping("/{id}")
    public ApiResult<ProductResponse> update(@PathVariable Long id, @Valid @RequestBody UpdateProductRequest request,
                                             @AuthenticationPrincipal Jwt jwt) {
        ProductResponse before = products.get(id);
        ProductResponse after = products.adminUpdate(id, request);
        audit.record(jwt.getSubject(), "PRODUCT_UPDATED", id,
                Map.of("name", before.name(), "price", before.price()),
                Map.of("name", after.name(), "price", after.price()));
        return ApiResult.success("Product updated successfully.", after);
    }

    @PatchMapping("/{id}/status")
    public ApiResult<ProductResponse> updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateProductStatusRequest request,
                                                   @AuthenticationPrincipal Jwt jwt) {
        ProductResponse before = products.get(id);
        ProductResponse after = products.adminUpdateStatus(id, request.status());
        String action = request.status() == ProductStatus.ACTIVE ? "PRODUCT_ACTIVATED" : "PRODUCT_DEACTIVATED";
        audit.record(jwt.getSubject(), action, id, Map.of("status", before.status()), Map.of("status", after.status()));
        return ApiResult.success("Product status updated successfully.", after);
    }

    @GetMapping("/stats")
    public ApiResult<ProductStatsResponse> stats() {
        return ApiResult.success("Product statistics retrieved.", products.adminStats());
    }

    private Pageable pageable(int page, int size, String sort) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page must be non-negative and size must be 1-100.");
        }
        String[] parts = sort.split(",", -1);
        if (!java.util.Set.of("id", "name", "price", "status").contains(parts[0])) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported sort field.");
        }
        Sort.Direction direction = parts.length > 1 ? Sort.Direction.fromOptionalString(parts[1]).orElse(null) : Sort.Direction.DESC;
        if (direction == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sort direction must be asc or desc.");
        return PageRequest.of(page, size, Sort.by(direction, parts[0]));
    }
}
