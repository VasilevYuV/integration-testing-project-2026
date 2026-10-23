package com.vasilevyuv.integrationtesting.users.user.api;

import com.vasilevyuv.integrationtesting.users.user.application.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/users")
public class UserController {
    private final UserService users;

    public UserController(UserService users) {
        this.users = users;
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        var user = users.create(request.name(), request.email());
        return ResponseEntity.created(URI.create("/users/" + user.id()))
                .body(UserResponse.from(user));
    }

    @GetMapping("/{id}")
    public UserResponse getById(@PathVariable UUID id) {
        return UserResponse.from(users.getById(id));
    }
}
