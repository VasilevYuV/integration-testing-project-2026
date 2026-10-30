package com.vasilevyuv.integrationtesting.orders.order.bottomup;

import com.vasilevyuv.integrationtesting.orders.order.application.OrderNotFoundException;
import com.vasilevyuv.integrationtesting.orders.order.application.OrderService;
import com.vasilevyuv.integrationtesting.orders.order.application.UnknownUserException;
import com.vasilevyuv.integrationtesting.orders.order.application.UserServiceUnavailableException;
import com.vasilevyuv.integrationtesting.orders.order.domain.Order;
import com.vasilevyuv.integrationtesting.orders.order.infrastructure.userclient.HttpUserDirectory;
import com.vasilevyuv.integrationtesting.orders.order.infrastructure.userclient.UserClientProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Stage 2: the test drives the service with real JDBC/H2 and the real HTTP client.
@Tag("bottomup-service")
class BottomUpServiceTest {
    private OrderDatabase database;
    private UserApiStub users;
    private OrderService orders;

    @BeforeEach
    void connectLowerLayers() {
        database = new OrderDatabase();
        users = new UserApiStub();
        var properties = new UserClientProperties(URI.create(users.baseUrl()),
                Duration.ofSeconds(1), Duration.ofSeconds(2));
        var directory = new HttpUserDirectory(RestClient.builder(), properties);
        orders = new OrderService(database.orders, directory);
    }

    @AfterEach
    void closeLowerLayers() {
        if (users != null) {
            users.close();
        }
        if (database != null) {
            database.close();
        }
    }

    @Test
    @DisplayName("BU-S01 Service fetches user over HTTP and saves a normalized order in H2")
    void createsOrderUsingRealLowerLayers() {
        var details = orders.create(UserApiStub.USER_ID, " Keyboard ", 3, new BigDecimal("19.99"));

        assertThat(details.order().product()).isEqualTo("Keyboard");
        assertThat(details.order().total()).isEqualByComparingTo("59.97");
        assertThat(details.user().id()).isEqualTo(UserApiStub.USER_ID);
        assertThat(details.user().name()).isEqualTo("Anna Test");
        assertThat(details.user().email()).isEqualTo("anna@example.com");
        assertThat(database.orders.findById(details.order().id())).contains(details.order());
        assertThat(database.countOrders()).isEqualTo(1);
        assertThat(users.requests()).containsExactly("GET /users/" + UserApiStub.USER_ID);
    }

    @Test
    @DisplayName("BU-S02 Service reads persisted order and fetches current user through HTTP")
    void readsSavedOrderWithCurrentUserData() {
        var saved = new Order(UUID.randomUUID(), UserApiStub.USER_ID, "Mouse", 2, new BigDecimal("10.50"));
        database.orders.save(saved);
        users.respond(200, UserApiStub.USER_JSON.replace("Anna Test", "Anna Updated"));

        var details = orders.getById(saved.id());

        assertThat(details.order()).isEqualTo(saved);
        assertThat(details.order().total()).isEqualByComparingTo("21.00");
        assertThat(details.user().name()).isEqualTo("Anna Updated");
        assertThat(users.requests()).containsExactly("GET /users/" + saved.userId());
    }

    @Test
    @DisplayName("BU-S03 Missing database row prevents any outgoing HTTP request")
    void missingOrderStopsBeforeHttpCall() {
        assertThatThrownBy(() -> orders.getById(UUID.randomUUID()))
                .isInstanceOf(OrderNotFoundException.class);

        assertThat(users.requests()).isEmpty();
        assertThat(database.countOrders()).isZero();
    }

    @Test
    @DisplayName("BU-S04 Upstream HTTP 404 produces UnknownUserException and no saved order")
    void unknownUserPreventsDatabaseWrite() {
        users.respond(404, "{\"message\":\"User not found\"}");

        assertThatThrownBy(() -> orders.create(UserApiStub.USER_ID, "Keyboard", 1, new BigDecimal("10.00")))
                .isInstanceOf(UnknownUserException.class);

        assertThat(users.requests()).containsExactly("GET /users/" + UserApiStub.USER_ID);
        assertThat(database.countOrders()).isZero();
    }

    @Test
    @DisplayName("BU-S05 Upstream HTTP 500 produces dependency exception and no saved order")
    void upstreamFailurePreventsDatabaseWrite() {
        users.respond(500, "{\"message\":\"Unavailable\"}");

        assertThatThrownBy(() -> orders.create(UserApiStub.USER_ID, "Keyboard", 1, new BigDecimal("10.00")))
                .isInstanceOf(UserServiceUnavailableException.class);

        assertThat(users.requests()).containsExactly("GET /users/" + UserApiStub.USER_ID);
        assertThat(database.countOrders()).isZero();
    }
}
