package com.vasilevyuv.integrationtesting.orders.order.bottomup;

import com.vasilevyuv.integrationtesting.orders.order.support.UserApiStub;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Stage 3: add the actual HTTP controller and server above the connected lower layers.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:bottomup-api")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Tag("bottomup-api")
class BottomUpApiTest {
    private static final UserApiStub USERS = new UserApiStub();
    private final JsonMapper json = JsonMapper.builder().build();
    private HttpClient http;

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcClient jdbc;

    @DynamicPropertySource
    static void configureUserApi(DynamicPropertyRegistry properties) {
        properties.add("clients.users.base-url", USERS::baseUrl);
    }

    @BeforeEach
    void resetState() {
        jdbc.sql("DELETE FROM orders").update();
        USERS.reset();
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    }

    @AfterEach
    void closeHttpClient() {
        http.close();
    }

    @AfterAll
    static void stopUserApi() {
        USERS.close();
    }

    @Test
    @DisplayName("BU-A01 HTTP create and read traverse all orders layers and the user HTTP client")
    void createsAndReadsOrderOverHttp() throws Exception {
        var created = postOrder(2);
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        assertThat(created.headers().firstValue("Content-Type").orElseThrow()).contains("application/json");
        var order = json.readTree(created.body());
        var id = UUID.fromString(order.get("id").asString());
        assertThat(order.get("product").asString()).isEqualTo("Keyboard");
        assertThat(order.get("quantity").asInt()).isEqualTo(2);
        assertThat(order.get("unitPrice").decimalValue()).isEqualByComparingTo("299.90");
        assertThat(order.get("total").decimalValue()).isEqualByComparingTo("599.80");
        assertThat(order.get("user").get("id").asString()).isEqualTo(UserApiStub.USER_ID.toString());
        assertThat(created.headers().firstValue("Location")).contains("/orders/" + id);
        assertThat(jdbc.sql("SELECT user_id FROM orders WHERE id = :id")
                .param("id", id).query(UUID.class).single()).isEqualTo(UserApiStub.USER_ID);

        var read = getOrder(id);
        assertThat(read.statusCode()).as(read.body()).isEqualTo(200);
        assertThat(json.readTree(read.body())).isEqualTo(order);
        assertThat(countOrders()).isEqualTo(1);
        assertThat(USERS.requests()).containsExactly(
                "GET /users/" + UserApiStub.USER_ID, "GET /users/" + UserApiStub.USER_ID);
    }

    @Test
    @DisplayName("BU-A02 Controller rejects invalid quantity before HTTP or database work")
    void invalidInputDoesNotReachLowerLayers() throws Exception {
        assertProblem(postOrder(0), 400, "INVALID_REQUEST");

        assertThat(USERS.requests()).isEmpty();
        assertThat(countOrders()).isZero();
    }

    @Test
    @DisplayName("BU-A03 Upstream 404 crosses the service boundary as API 422 without a database row")
    void unknownUserBecomes422() throws Exception {
        USERS.respond(404, "{\"message\":\"User not found\"}");

        assertProblem(postOrder(2), 422, "USER_NOT_FOUND");

        assertThat(USERS.requests()).containsExactly("GET /users/" + UserApiStub.USER_ID);
        assertThat(countOrders()).isZero();
    }

    @Test
    @DisplayName("BU-A04 Upstream 500 crosses the service boundary as API 503 without a database row")
    void upstreamFailureBecomes503() throws Exception {
        USERS.respond(500, "{\"message\":\"Unavailable\"}");

        assertProblem(postOrder(2), 503, "USER_SERVICE_UNAVAILABLE");

        assertThat(USERS.requests()).containsExactly("GET /users/" + UserApiStub.USER_ID);
        assertThat(countOrders()).isZero();
    }

    @Test
    @DisplayName("BU-A05 Failed user lookup does not delete an existing order and reading recovers")
    void readRecoversAfterUserApiFailure() throws Exception {
        var created = postOrder(2);
        assertThat(created.statusCode()).isEqualTo(201);
        var id = UUID.fromString(json.readTree(created.body()).get("id").asString());
        USERS.respond(500, "{\"message\":\"Unavailable\"}");

        assertProblem(getOrder(id), 503, "USER_SERVICE_UNAVAILABLE");
        assertThat(countOrders()).isEqualTo(1);

        USERS.respond(200, UserApiStub.USER_JSON);
        var recovered = getOrder(id);
        assertThat(recovered.statusCode()).isEqualTo(200);
        assertThat(json.readTree(recovered.body())).isEqualTo(json.readTree(created.body()));
        assertThat(countOrders()).isEqualTo(1);
    }

    private HttpResponse<String> postOrder(int quantity) throws Exception {
        var body = """
                {"userId":"%s", "product":" Keyboard ", "quantity":%d, "unitPrice":299.90}
                """.formatted(UserApiStub.USER_ID, quantity);
        return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/orders"))
                .timeout(Duration.ofSeconds(5)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> getOrder(UUID id) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/orders/" + id))
                .timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private long countOrders() {
        return jdbc.sql("SELECT COUNT(*) FROM orders").query(Long.class).single();
    }

    private void assertProblem(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow()).contains("application/problem+json");
        var problem = json.readTree(response.body());
        assertThat(problem.get("status").asInt()).isEqualTo(status);
        assertThat(problem.get("code").asString()).isEqualTo(code);
    }
}
