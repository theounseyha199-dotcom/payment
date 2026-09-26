package com.practice.product.dto;

import com.practice.product.entity.ProductStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateProductStatusRequest(@NotNull ProductStatus status) { }
