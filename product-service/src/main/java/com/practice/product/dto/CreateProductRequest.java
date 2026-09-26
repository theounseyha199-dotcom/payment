package com.practice.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record CreateProductRequest(
        @NotBlank String name,
        @NotNull @DecimalMin(value = "1") @Digits(integer = 10, fraction = 0) BigDecimal price
) { }
