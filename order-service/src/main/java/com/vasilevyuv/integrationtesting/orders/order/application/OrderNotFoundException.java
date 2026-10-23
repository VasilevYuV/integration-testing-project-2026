package com.vasilevyuv.integrationtesting.orders.order.application;

import java.util.UUID;

public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException(UUID id) {
        super("Order not found: " + id);
    }
}
