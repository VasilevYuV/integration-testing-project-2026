package com.vasilevyuv.integrationtesting.orders.order.application;

import com.vasilevyuv.integrationtesting.orders.order.domain.Order;

public record OrderDetails(Order order, UserSummary user) {
}
