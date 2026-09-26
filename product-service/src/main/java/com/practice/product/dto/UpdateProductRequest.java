package com.practice.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record UpdateProductRequest(
        @Size(min = 1, max = 255) String name,
        @DecimalMin(value = "0.01") @Digits(integer = 10, fraction = 2) BigDecimal price
) { }
