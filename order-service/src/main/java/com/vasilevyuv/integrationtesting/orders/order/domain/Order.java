package com.vasilevyuv.integrationtesting.orders.order.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record Order(UUID id, UUID userId, String product, int quantity, BigDecimal unitPrice) {
    public BigDecimal total() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
