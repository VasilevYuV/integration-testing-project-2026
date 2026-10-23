package com.vasilevyuv.integrationtesting.orders.order.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateOrderRequest(
        @NotNull UUID userId,
        @NotBlank @Size(max = 200) String product,
        @Min(1) @Max(1000) int quantity,
        @NotNull @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal unitPrice
) {
}
