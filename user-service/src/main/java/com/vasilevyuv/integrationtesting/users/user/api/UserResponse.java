package com.vasilevyuv.integrationtesting.users.user.api;

import com.vasilevyuv.integrationtesting.users.user.domain.User;

import java.util.UUID;

public record UserResponse(UUID id, String name, String email) {
    public static UserResponse from(User user) {
        return new UserResponse(user.id(), user.name(), user.email());
    }
}
