package com.vasilevyuv.integrationtesting.orders.order.hybrid;

import com.vasilevyuv.integrationtesting.orders.order.application.OrderDetails;
import com.vasilevyuv.integrationtesting.orders.order.application.UserSummary;
import com.vasilevyuv.integrationtesting.orders.order.domain.Order;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.stream.Stream;

/** The same input/output contract is checked independently above and below the service. */
final class HybridOrderCases {
    static final OrderCase MINIMUM = new OrderCase("minimum price, Unicode and apostrophe",
            UUID.fromString("71111111-1111-4111-8111-111111111111"),
            UUID.fromString("72111111-1111-4111-8111-111111111111"),
            " Клавиатура O'Reilly ", "Клавиатура O'Reilly", 1,
            new BigDecimal("0.01"), new BigDecimal("0.01"));

    static final OrderCase MAXIMUM = new OrderCase("maximum price and quantity",
            UUID.fromString("71222222-2222-4222-8222-222222222222"),
            UUID.fromString("72222222-2222-4222-8222-222222222222"),
            " Workstation ", "Workstation", 1000,
            new BigDecimal("99999999.99"), new BigDecimal("99999999990.00"));

    static Stream<OrderCase> validOrders() {
        return Stream.of(MINIMUM, MAXIMUM);
    }

    record OrderCase(String label, UUID orderId, UUID userId, String inputProduct,
                     String storedProduct, int quantity, BigDecimal price, BigDecimal total) {
        String requestJson() {
            return """
                    {"userId":"%s","product":"%s","quantity":%d,"unitPrice":%s}
                    """.formatted(userId, inputProduct, quantity, price);
        }

        String userJson() {
            return """
                    {"id":"%s","name":"Hybrid Test","email":"hybrid@example.com"}
                    """.formatted(userId);
        }

        Order order(UUID id) {
            return new Order(id, userId, storedProduct, quantity, price);
        }

        OrderDetails details() {
            return new OrderDetails(order(orderId), new UserSummary(userId, "Hybrid Test", "hybrid@example.com"));
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
