package com.vasilevyuv.integrationtesting.services;

import com.vasilevyuv.integrationtesting.support.RunningService;
import com.vasilevyuv.integrationtesting.support.ResponseTiming;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ServiceApiIT {
    private final JsonMapper json = JsonMapper.builder().build();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private RunningService users;
    private RunningService orders;

    @BeforeAll
    void startServices() throws Exception {
        users = RunningService.start(Path.of(System.getProperty("userServiceJar")));
        orders = RunningService.start(Path.of(System.getProperty("orderServiceJar")),
                "--clients.users.base-url=" + users.baseUrl());
    }

    @AfterAll
    void stopServices() throws Exception {
        try {
            if (orders != null) {
                orders.close();
            }
        } finally {
            try {
                if (users != null) {
                    users.close();
                }
            } finally {
                http.close();
            }
        }
    }

    @Test
    void createsAndReadsOrderWithUserDataFromAnotherService() throws Exception {
        var createdUser = post(users, "/users", """
                {"name":"Anna Test", "email":"anna@example.com"}
                """);
        assertThat(createdUser.statusCode()).isEqualTo(201);
        assertJson(createdUser);
        var user = json.readTree(createdUser.body());
        var userId = user.get("id").asString();
        assertThat(UUID.fromString(userId)).isNotNull();
        assertThat(user.get("name").asString()).isEqualTo("Anna Test");
        assertThat(user.get("email").asString()).isEqualTo("anna@example.com");
        assertThat(createdUser.headers().firstValue("Location")).contains("/users/" + userId);

        var readUser = get(users, "/users/" + userId);
        assertThat(readUser.statusCode()).isEqualTo(200);
        assertThat(json.readTree(readUser.body())).isEqualTo(user);

        var createdOrder = post(orders, "/orders", """
                {"userId":"%s", "product":"Keyboard", "quantity":2, "unitPrice":299.90}
                """.formatted(userId));
        assertThat(createdOrder.statusCode()).isEqualTo(201);
        assertJson(createdOrder);
        var order = json.readTree(createdOrder.body());
        var orderId = order.get("id").asString();
        assertThat(UUID.fromString(orderId)).isNotNull();
        assertThat(order.get("userId").asString()).isEqualTo(userId);
        assertThat(order.get("product").asString()).isEqualTo("Keyboard");
        assertThat(order.get("quantity").asInt()).isEqualTo(2);
        assertThat(order.get("unitPrice").decimalValue()).isEqualByComparingTo("299.90");
        assertThat(order.get("total").decimalValue()).isEqualByComparingTo("599.80");
        assertThat(order.get("user")).isEqualTo(user);
        assertThat(createdOrder.headers().firstValue("Location")).contains("/orders/" + orderId);

        var readOrder = get(orders, "/orders/" + orderId);
        assertThat(readOrder.statusCode()).isEqualTo(200);
        assertThat(json.readTree(readOrder.body())).isEqualTo(order);
    }

    @Test
    void rejectsInvalidRequestFieldsAndIdentifiers() throws Exception {
        assertProblem(post(users, "/users", """
                {"name":"", "email":"invalid"}
                """), 400, "INVALID_REQUEST");
        assertProblem(post(orders, "/orders", """
                {"userId":"%s", "product":"Keyboard", "quantity":0, "unitPrice":10.00}
                """.formatted(UUID.randomUUID())), 400, "INVALID_REQUEST");
        assertProblem(post(orders, "/orders", """
                {"userId":"%s", "product":"Keyboard", "quantity":1.5, "unitPrice":10.00}
                """.formatted(UUID.randomUUID())), 400, "INVALID_REQUEST");
        assertProblem(post(users, "/users", "not-json"), 400, "INVALID_REQUEST");
        assertProblem(post(orders, "/orders", "not-json"), 400, "INVALID_REQUEST");
        assertProblem(get(users, "/users/not-a-uuid"), 400, "INVALID_REQUEST");
        assertProblem(get(orders, "/orders/not-a-uuid"), 400, "INVALID_REQUEST");
    }

    @Test
    void returnsNotFoundForUnknownResources() throws Exception {
        assertProblem(get(users, "/users/" + UUID.randomUUID()), 404, "USER_NOT_FOUND");
        assertProblem(get(orders, "/orders/" + UUID.randomUUID()), 404, "ORDER_NOT_FOUND");
    }

    @Test
    void rejectsOrderWhenUserDoesNotExist() throws Exception {
        assertProblem(post(orders, "/orders", """
                {"userId":"%s", "product":"Keyboard", "quantity":1, "unitPrice":10.00}
                """.formatted(UUID.randomUUID())), 422, "USER_NOT_FOUND");
    }

    @Test
    void readsOrderThroughBothServicesWithinLocalResponseBudget() throws Exception {
        var user = post(users, "/users", """
                {"name":"Timing User", "email":"timing@example.com"}
                """);
        assertThat(user.statusCode()).isEqualTo(201);
        var userId = json.readTree(user.body()).get("id").asString();
        var order = post(orders, "/orders", """
                {"userId":"%s", "product":"Mouse", "quantity":1, "unitPrice":10.00}
                """.formatted(userId));
        assertThat(order.statusCode()).isEqualTo(201);
        var path = "/orders/" + json.readTree(order.body()).get("id").asString();
        assertThat(get(orders, path).statusCode()).isEqualTo(200); // Warm up before measuring.
        for (int sample = 1; sample <= 5; sample++) {
            long startedAt = System.nanoTime();
            var response = get(orders, path);
            long elapsedMs = ResponseTiming.record("real-services-order-" + sample, startedAt);
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(json.readTree(response.body()).get("user").get("id").asString()).isEqualTo(userId);
            assertThat(elapsedMs).as("Local end-to-end budget, ms").isLessThan(2000);
        }
    }

    private HttpResponse<String> post(RunningService service, String path, String body) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(service.baseUrl() + path))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(RunningService service, String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(service.baseUrl() + path))
                .timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private void assertJson(HttpResponse<String> response) {
        assertThat(response.headers().firstValue("Content-Type").orElseThrow()).contains("application/json");
    }

    private void assertProblem(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow())
                .contains("application/problem+json");
        JsonNode problem = json.readTree(response.body());
        assertThat(problem.get("status").asInt()).isEqualTo(status);
        assertThat(problem.get("code").asString()).isEqualTo(code);
    }
}
