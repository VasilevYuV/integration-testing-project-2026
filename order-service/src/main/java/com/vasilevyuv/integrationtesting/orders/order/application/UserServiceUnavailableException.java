package com.vasilevyuv.integrationtesting.orders.order.application;

public class UserServiceUnavailableException extends RuntimeException {
    public UserServiceUnavailableException(String message) {
        super(message);
    }

    public UserServiceUnavailableException(Throwable cause) {
        super("User service is temporarily unavailable.", cause);
    }
}
