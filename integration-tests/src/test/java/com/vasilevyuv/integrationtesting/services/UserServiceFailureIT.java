package com.vasilevyuv.integrationtesting.services;

import com.vasilevyuv.integrationtesting.support.ResponseTiming;
import com.vasilevyuv.integrationtesting.support.RunningService;
import com.vasilevyuv.integrationtesting.support.UserServiceStub;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
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
class UserServiceFailureIT {
    private final JsonMapper json = JsonMapper.builder().build();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private UserServiceStub stub;
    private RunningService orders;

    @BeforeAll
    void startServices() throws Exception {
        stub = new UserServiceStub();
        orders = RunningService.start(orderJar(), "--clients.users.base-url=" + stub.baseUrl(),
                "--clients.users.read-timeout=400ms");
    }

    @BeforeEach
    void resetStub() {
        stub.respond(200, UserServiceStub.USER_JSON, Duration.ZERO);
    }

    @AfterAll
    void stopServices() throws Exception {
        try {
            if (orders != null) orders.close();
        } finally {
            if (stub != null) stub.close();
            http.close();
        }
    }

    @Test
    void returns503ForCreateAndReadAfterRealUserServiceStops() throws Exception {
        try (var realUsers = RunningService.start(Path.of(System.getProperty("userServiceJar")));
             var realOrders = RunningService.start(orderJar(),
                     "--clients.users.base-url=" + realUsers.baseUrl())) {
            var user = post(realUsers, "/users", """
                    {"name":"Outage User","email":"outage@example.com"}
                    """);
            assertThat(user.statusCode()).isEqualTo(201);
            var userId = json.readTree(user.body()).get("id").asString();
            var order = post(realOrders, "/orders", orderBody(userId));
            assertThat(order.statusCode()).isEqualTo(201);
            var orderId = json.readTree(order.body()).get("id").asString();
            realUsers.close();

            long startedAt = System.nanoTime();
            var createResponse = post(realOrders, "/orders", orderBody(userId));
            long elapsedMs = ResponseTiming.record("stopped-user-service", startedAt);
            assertUnavailable(createResponse);
            assertThat(elapsedMs).isLessThan(2000);
            assertUnavailable(get(realOrders, "/orders/" + orderId));
        }
    }

    @Test
    void translatesUpstreamServerErrorTo503() throws Exception {
        stub.respond(500, "{\"error\":\"database unavailable\"}", Duration.ZERO);
        assertUnavailable(createOrder());
    }

    @Test
    void rejectsMalformedUpstreamJson() throws Exception {
        stub.respond(200, "not-json", Duration.ZERO);
        assertUnavailable(createOrder());
    }

    @Test
    void rejectsUpstreamUserWithAnotherId() throws Exception {
        stub.respond(200, UserServiceStub.USER_JSON.replace(UserServiceStub.USER_ID.toString(),
                UUID.randomUUID().toString()), Duration.ZERO);
        assertUnavailable(createOrder());
    }

    @Test
    void rejectsIncompleteUpstreamUser() throws Exception {
        stub.respond(200, "{\"id\":\"" + UserServiceStub.USER_ID + "\"}", Duration.ZERO);
        assertUnavailable(createOrder());
    }

    @Test
    void timesOutInsteadOfWaitingForSlowUserService() throws Exception {
        assertThat(createOrder().statusCode()).isEqualTo(201); // Warm up the HTTP path.
        stub.respond(200, UserServiceStub.USER_JSON, Duration.ofMillis(1500));
        long startedAt = System.nanoTime();
        var response = createOrder();
        long elapsedMs = ResponseTiming.record("read-timeout-400ms", startedAt);
        assertUnavailable(response);
        assertThat(stub.lastPath()).isEqualTo("/users/" + UserServiceStub.USER_ID);
        assertThat(elapsedMs).isBetween(300L, 1200L);
    }

    @Test
    void acceptsDelayBelowReadTimeout() throws Exception {
        assertThat(createOrder().statusCode()).isEqualTo(201);
        stub.respond(200, UserServiceStub.USER_JSON, Duration.ofMillis(150));
        long startedAt = System.nanoTime();
        var response = createOrder();
        long elapsedMs = ResponseTiming.record("upstream-delay-150ms", startedAt);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        assertThat(json.readTree(response.body()).get("user").get("id").asString())
                .isEqualTo(UserServiceStub.USER_ID.toString());
        assertThat(elapsedMs).isBetween(100L, 1200L);
    }

    private HttpResponse<String> createOrder() throws Exception {
        return post(orders, "/orders", orderBody(UserServiceStub.USER_ID.toString()));
    }

    private static String orderBody(String userId) {
        return """
                {"userId":"%s","product":"Keyboard","quantity":2,"unitPrice":299.90}
                """.formatted(userId);
    }

    private HttpResponse<String> post(RunningService service, String path, String body) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(service.baseUrl() + path))
                .timeout(Duration.ofSeconds(5)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(RunningService service, String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(service.baseUrl() + path))
                .timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private void assertUnavailable(HttpResponse<String> response) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(503);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow())
                .contains("application/problem+json");
        var problem = json.readTree(response.body());
        assertThat(problem.get("status").asInt()).isEqualTo(503);
        assertThat(problem.get("code").asString()).isEqualTo("USER_SERVICE_UNAVAILABLE");
    }

    private static Path orderJar() {
        return Path.of(System.getProperty("orderServiceJar"));
    }
}
