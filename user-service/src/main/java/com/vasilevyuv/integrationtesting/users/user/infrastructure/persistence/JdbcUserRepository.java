package com.vasilevyuv.integrationtesting.users.user.infrastructure.persistence;

import com.vasilevyuv.integrationtesting.users.user.domain.User;
import com.vasilevyuv.integrationtesting.users.user.domain.UserRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcUserRepository implements UserRepository {
    private final JdbcClient jdbc;

    public JdbcUserRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(User user) {
        jdbc.sql("INSERT INTO users (id, name, email) VALUES (:id, :name, :email)")
                .param("id", user.id())
                .param("name", user.name())
                .param("email", user.email())
                .update();
    }

    @Override
    public Optional<User> findById(UUID id) {
        return jdbc.sql("SELECT id, name, email FROM users WHERE id = :id")
                .param("id", id)
                .query((row, rowNumber) -> new User(row.getObject("id", UUID.class),
                        row.getString("name"), row.getString("email")))
                .optional();
    }
}
