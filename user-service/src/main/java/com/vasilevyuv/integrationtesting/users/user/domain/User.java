package com.vasilevyuv.integrationtesting.users.user.domain;

import java.util.UUID;

public record User(UUID id, String name, String email) {
}
