package com.vasilevyuv.integrationtesting.orders.order.application;

import java.util.UUID;

public class UnknownUserException extends RuntimeException {
    public UnknownUserException(UUID id) {
        super("User not found in user-service: " + id);
    }
}
