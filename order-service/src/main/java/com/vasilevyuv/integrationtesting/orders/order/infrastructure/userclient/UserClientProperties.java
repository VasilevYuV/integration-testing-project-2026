package com.vasilevyuv.integrationtesting.orders.order.infrastructure.userclient;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;

@Validated
@ConfigurationProperties("clients.users")
public record UserClientProperties(@NotNull URI baseUrl, @NotNull Duration connectTimeout,
                                   @NotNull Duration readTimeout) {
    public UserClientProperties {
        if (connectTimeout != null && !connectTimeout.isPositive()) {
            throw new IllegalArgumentException("Connect timeout must be positive");
        }
        if (readTimeout != null && !readTimeout.isPositive()) {
            throw new IllegalArgumentException("Read timeout must be positive");
        }
    }
}
