package com.vasilevyuv.integrationtesting.orders.order.topdown;

import com.vasilevyuv.integrationtesting.orders.order.application.OrderDetails;
import com.vasilevyuv.integrationtesting.orders.order.application.UserSummary;
import com.vasilevyuv.integrationtesting.orders.order.domain.Order;

import java.math.BigDecimal;
import java.util.UUID;

final class OrderTestData {
    static final UUID USER_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    static final UUID ORDER_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    static final BigDecimal PRICE = new BigDecimal("299.90");
    static final UserSummary USER = new UserSummary(USER_ID, "Anna Test", "anna@example.com");
    static final Order ORDER = new Order(ORDER_ID, USER_ID, "Keyboard", 2, PRICE);
    static final OrderDetails DETAILS = new OrderDetails(ORDER, USER);

    private OrderTestData() {
    }

    static String request(int quantity) {
        return """
                {"userId":"%s", "product":" Keyboard ", "quantity":%d, "unitPrice":299.90}
                """.formatted(USER_ID, quantity);
    }
}
