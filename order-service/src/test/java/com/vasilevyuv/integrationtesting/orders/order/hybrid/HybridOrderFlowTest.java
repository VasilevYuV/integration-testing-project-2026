package com.vasilevyuv.integrationtesting.orders.order.hybrid;

import com.vasilevyuv.integrationtesting.orders.order.support.UserApiStub;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.vasilevyuv.integrationtesting.orders.order.hybrid.HybridOrderCases.*;
import static org.assertj.core.api.Assertions.assertThat;

// Join both branches at the real service; observe each HTTP result and persisted state.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:hybrid-flow", "clients.users.read-timeout=400ms"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Tag("hybrid-flow")
class HybridOrderFlowTest {
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

    @ParameterizedTest(name = "HY-J01 [{index}] Joined API and storage contract: {0}")
    @MethodSource("com.vasilevyuv.integrationtesting.orders.order.hybrid.HybridOrderCases#validOrders")
    void createsAndReadsSharedContract(OrderCase sample) throws Exception {
        USERS.respond(200, sample.userJson());
        var created = post(sample.requestJson());
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        assertThat(created.headers().firstValue("Content-Type").orElseThrow()).contains("application/json");
        var body = json.readTree(created.body());
        var id = UUID.fromString(body.get("id").asString());
        assertThat(created.headers().firstValue("Location")).contains("/orders/" + id);
        assertOrderBody(body, sample, id);
        assertStoredOrder(sample, id);

        var read = get(id);
        assertThat(read.statusCode()).as(read.body()).isEqualTo(200);
        assertOrderBody(json.readTree(read.body()), sample, id);
        assertThat(countOrders()).isEqualTo(1);
        assertThat(USERS.requests()).containsExactly("GET /users/" + sample.userId(), "GET /users/" + sample.userId());
    }

    @Test
    @DisplayName("HY-J02 A row seeded from below is readable through the complete API")
    void readsSeededOrder() throws Exception {
        seedOrder(MINIMUM);
        USERS.respond(200, MINIMUM.userJson());

        var read = get(MINIMUM.orderId());
        assertThat(read.statusCode()).as(read.body()).isEqualTo(200);
        assertOrderBody(json.readTree(read.body()), MINIMUM, MINIMUM.orderId());
        assertOnlySeedRemains();
        assertThat(USERS.requests()).containsExactly("GET /users/" + MINIMUM.userId());
    }

    @Test
    @DisplayName("HY-J03 Invalid price neither calls users nor changes existing data")
    void rejectsInvalidInputWithoutSideEffects() throws Exception {
        seedOrder(MINIMUM);

        assertProblem(post(MINIMUM.requestJson().replace("0.01", "0.019")), 400, "INVALID_REQUEST");

        assertOnlySeedRemains();
        assertThat(USERS.requests()).isEmpty();
    }

    @ParameterizedTest(name = "HY-J04 [{index}] users {0} becomes orders {1}; stored data is unchanged")
    @CsvSource({"404, 422, USER_NOT_FOUND", "500, 503, USER_SERVICE_UNAVAILABLE"})
    void rejectsDependencyFailuresWithoutSideEffects(int upstreamStatus, int apiStatus, String code) throws Exception {
        seedOrder(MINIMUM);
        USERS.respond(upstreamStatus, "{\"message\":\"Controlled failure\"}");

        assertProblem(post(MAXIMUM.requestJson()), apiStatus, code);

        assertOnlySeedRemains();
        assertThat(USERS.requests()).containsExactly("GET /users/" + MAXIMUM.userId());
    }

    @Test
    @DisplayName("HY-J05 Timeout returns before the delayed reply and never persists a late order")
    void timeoutDoesNotPersistAnOrderAfterLateReply() throws Exception {
        seedOrder(MINIMUM);
        USERS.respond(200, MINIMUM.userJson());
        assertThat(get(MINIMUM.orderId()).statusCode()).isEqualTo(200); // Warm up the HTTP path.
        USERS.reset();
        var replyFinished = USERS.respondAfter(200, MAXIMUM.userJson(), Duration.ofMillis(1500));

        long started = System.nanoTime();
        var response = post(MAXIMUM.requestJson());
        var elapsed = Duration.ofNanos(System.nanoTime() - started);

        assertProblem(response, 503, "USER_SERVICE_UNAVAILABLE");
        assertThat(elapsed).isLessThan(Duration.ofMillis(1200));
        assertThat(replyFinished.isDone()).as("API must return before the delayed response attempt").isFalse();
        assertOnlySeedRemains();
        replyFinished.get(4, TimeUnit.SECONDS);
        assertOnlySeedRemains();
        assertThat(USERS.requests()).containsExactly("GET /users/" + MAXIMUM.userId());
    }

    private void seedOrder(OrderCase sample) {
        jdbc.sql("INSERT INTO orders (id, user_id, product, quantity, unit_price) VALUES (:id, :user, :product, :qty, :price)")
                .param("id", sample.orderId()).param("user", sample.userId()).param("product", sample.storedProduct())
                .param("qty", sample.quantity()).param("price", sample.price()).update();
    }

    private void assertOnlySeedRemains() {
        assertThat(countOrders()).isEqualTo(1);
        assertStoredOrder(MINIMUM, MINIMUM.orderId());
    }

    private void assertStoredOrder(OrderCase sample, UUID id) {
        var row = jdbc.sql("SELECT id, user_id, product, quantity, unit_price FROM orders WHERE id = :id")
                .param("id", id).query().singleRow();
        assertThat(row.get("ID")).isEqualTo(id);
        assertThat(row.get("USER_ID")).isEqualTo(sample.userId());
        assertThat(row.get("PRODUCT")).isEqualTo(sample.storedProduct());
        assertThat(row.get("QUANTITY")).isEqualTo(sample.quantity());
        assertThat((BigDecimal) row.get("UNIT_PRICE")).isEqualByComparingTo(sample.price());
    }

    private void assertOrderBody(JsonNode body, OrderCase sample, UUID id) {
        assertThat(body.get("id").asString()).isEqualTo(id.toString());
        assertThat(body.get("userId").asString()).isEqualTo(sample.userId().toString());
        assertThat(body.get("product").asString()).isEqualTo(sample.storedProduct());
        assertThat(body.get("quantity").asInt()).isEqualTo(sample.quantity());
        assertThat(body.get("unitPrice").decimalValue()).isEqualByComparingTo(sample.price());
        assertThat(body.get("total").decimalValue()).isEqualByComparingTo(sample.total());
        assertThat(body.get("user").get("id").asString()).isEqualTo(sample.userId().toString());
        assertThat(body.get("user").get("name").asString()).isEqualTo("Hybrid Test");
        assertThat(body.get("user").get("email").asString()).isEqualTo("hybrid@example.com");
    }

    private long countOrders() {
        return jdbc.sql("SELECT COUNT(*) FROM orders").query(Long.class).single();
    }

    private HttpResponse<String> post(String body) throws Exception {
        return http.send(request("/orders").header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(UUID id) throws Exception {
        return http.send(request("/orders/" + id).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(5));
    }

    private void assertProblem(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow()).contains("application/problem+json");
        var problem = json.readTree(response.body());
        assertThat(problem.get("status").asInt()).isEqualTo(status);
        assertThat(problem.get("code").asString()).isEqualTo(code);
    }
}
