package com.vasilevyuv.integrationtesting.orders.order.api;

import com.vasilevyuv.integrationtesting.orders.order.application.OrderDetails;
import com.vasilevyuv.integrationtesting.orders.order.application.UserSummary;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderResponse(UUID id, UUID userId, String product, int quantity,
                            BigDecimal unitPrice, BigDecimal total, UserSummary user) {
    public static OrderResponse from(OrderDetails details) {
        var order = details.order();
        return new OrderResponse(order.id(), order.userId(), order.product(), order.quantity(),
                order.unitPrice(), order.total(), details.user());
    }
}
