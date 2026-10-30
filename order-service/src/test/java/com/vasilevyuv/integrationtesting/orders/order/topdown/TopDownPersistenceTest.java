package com.vasilevyuv.integrationtesting.orders.order.topdown;

import com.vasilevyuv.integrationtesting.orders.order.application.UnknownUserException;
import com.vasilevyuv.integrationtesting.orders.order.application.UserDirectory;
import com.vasilevyuv.integrationtesting.orders.order.application.UserServiceUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static com.vasilevyuv.integrationtesting.orders.order.topdown.OrderTestData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Stage 3: real MVC, service, JDBC repository and H2; only the external user directory is a stub.
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:topdown-orders")
@AutoConfigureMockMvc
@Tag("topdown-persistence")
class TopDownPersistenceTest {
    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    @MockitoBean
    private UserDirectory users;

    @BeforeEach
    void clearDatabase() {
        jdbc.sql("DELETE FROM orders").update();
    }

    @Test
    @DisplayName("TD-P01 POST persists exact values in H2 and GET reads them back")
    void persistsOrderThroughControllerAndReadsItBack() throws Exception {
        when(users.getById(USER_ID)).thenReturn(USER);

        var response = mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(request(2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.total").value(599.80))
                .andReturn().getResponse();

        var row = jdbc.sql("SELECT id, user_id, product, quantity, unit_price FROM orders")
                .query((result, index) -> new StoredOrder(result.getObject("id", UUID.class),
                        result.getObject("user_id", UUID.class), result.getString("product"),
                        result.getInt("quantity"), result.getBigDecimal("unit_price")))
                .single();
        assertThat(row.userId()).isEqualTo(USER_ID);
        assertThat(row.product()).isEqualTo("Keyboard");
        assertThat(row.quantity()).isEqualTo(2);
        assertThat(row.unitPrice()).isEqualByComparingTo("299.90");
        assertThat(response.getHeader("Location")).isEqualTo("/orders/" + row.id());

        mvc.perform(get(response.getHeader("Location")))
                .andExpect(status().isOk())
                .andExpect(content().json(response.getContentAsString()));
        verify(users, times(2)).getById(USER_ID);
    }

    @Test
    @DisplayName("TD-P02 Unknown user leaves the actual database empty")
    void unknownUserDoesNotLeaveAnOrderInDatabase() throws Exception {
        when(users.getById(USER_ID)).thenThrow(new UnknownUserException(USER_ID));

        mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(request(2)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));

        assertDatabaseEmpty();
    }

    @Test
    @DisplayName("TD-P03 Dependency failure leaves the actual database empty")
    void unavailableUserServiceDoesNotLeaveAnOrderInDatabase() throws Exception {
        when(users.getById(USER_ID)).thenThrow(new UserServiceUnavailableException("Simulated outage"));

        mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(request(2)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("USER_SERVICE_UNAVAILABLE"));

        assertDatabaseEmpty();
    }

    @Test
    @DisplayName("TD-P04 Missing H2 row returns 404 without external lookup")
    void missingDatabaseRowStopsUserLookup() throws Exception {
        mvc.perform(get("/orders/{id}", ORDER_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));

        verifyNoInteractions(users);
        assertDatabaseEmpty();
    }

    private void assertDatabaseEmpty() {
        assertThat(jdbc.sql("SELECT COUNT(*) FROM orders").query(Long.class).single()).isZero();
    }

    private record StoredOrder(UUID id, UUID userId, String product, int quantity, BigDecimal unitPrice) {
    }
}
