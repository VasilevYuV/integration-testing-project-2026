package com.vasilevyuv.integrationtesting.users.user.domain;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository {
    void save(User user);

    Optional<User> findById(UUID id);
}
