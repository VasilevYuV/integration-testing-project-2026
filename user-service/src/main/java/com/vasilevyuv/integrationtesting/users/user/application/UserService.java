package com.vasilevyuv.integrationtesting.users.user.application;

import com.vasilevyuv.integrationtesting.users.user.domain.User;
import com.vasilevyuv.integrationtesting.users.user.domain.UserRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class UserService {
    private final UserRepository users;

    public UserService(UserRepository users) {
        this.users = users;
    }

    public User create(String name, String email) {
        var user = new User(UUID.randomUUID(), name.strip(), email.strip());
        users.save(user);
        return user;
    }

    public User getById(UUID id) {
        return users.findById(id).orElseThrow(() -> new UserNotFoundException(id));
    }
}
