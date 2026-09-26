package com.practice.product.service;

import com.practice.product.dto.CreateProductRequest;
import com.practice.product.dto.ProductResponse;
import com.practice.product.dto.PageResponse;
import com.practice.product.dto.ProductStatsResponse;
import com.practice.product.dto.UpdateProductRequest;
import com.practice.product.entity.Product;
import com.practice.product.entity.ProductStatus;
import com.practice.product.repository.ProductRepository;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;
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
        return products.findAll((root, query, builder) -> builder.or(
                        builder.equal(root.get("status"), ProductStatus.ACTIVE),
                        builder.isNull(root.get("status"))))
                .stream().map(ProductResponse::from).toList();
    }

    public PageResponse<ProductResponse> adminList(String search, ProductStatus status, Pageable pageable) {
        Specification<Product> specification = (root, query, builder) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (StringUtils.hasText(search)) {
                predicates.add(builder.like(builder.lower(root.get("name")), "%" + search.trim().toLowerCase() + "%"));
            }
            if (status == ProductStatus.ACTIVE) {
                predicates.add(builder.or(builder.equal(root.get("status"), ProductStatus.ACTIVE),
                        builder.isNull(root.get("status"))));
            } else if (status != null) {
                predicates.add(builder.equal(root.get("status"), status));
            }
            return predicates.isEmpty() ? builder.conjunction() : builder.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        Page<ProductResponse> page = products.findAll(specification, pageable).map(ProductResponse::from);
        return PageResponse.from(page);
    }

    public ProductResponse adminUpdate(Long id, UpdateProductRequest request) {
        if (request.name() == null && request.price() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide a name or price to update.");
        }
        Product product = find(id);
        product.update(request.name(), request.price());
        return ProductResponse.from(products.save(product));
    }

    public ProductResponse adminUpdateStatus(Long id, ProductStatus status) {
        Product product = find(id);
        product.setStatus(status);
        return ProductResponse.from(products.save(product));
    }

    public ProductStatsResponse adminStats() {
        long active = products.countByStatus(ProductStatus.ACTIVE) + products.countByStatusIsNull();
        long inactive = products.countByStatus(ProductStatus.INACTIVE);
        return new ProductStatsResponse(active + inactive, active, inactive);
    }

    private Product find(Long id) {
        return products.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found."));
    }
}
