package com.vasilevyuv.integrationtesting.users.user.application;

import java.util.UUID;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(UUID id) {
        super("User not found: " + id);
    }
}
