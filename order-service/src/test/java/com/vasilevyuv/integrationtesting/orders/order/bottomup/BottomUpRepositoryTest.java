package com.vasilevyuv.integrationtesting.orders.order.bottomup;

import com.vasilevyuv.integrationtesting.orders.order.domain.Order;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Stage 1: the test drives the real JDBC repository and H2; no service or controller.
@Tag("bottomup-repository")
class BottomUpRepositoryTest {
    private OrderDatabase database;

    @BeforeEach
    void openDatabase() {
        database = new OrderDatabase();
    }

    @AfterEach
    void closeDatabase() {
        database.close();
    }

    @Test
    @DisplayName("BU-R01 Repository preserves UUIDs, text, quantity and exact decimal price")
    void savesAndReadsOrderWithoutLosingValues() {
        var order = order(UUID.randomUUID(), 3, "19.99");

        database.orders.save(order);

        assertThat(database.orders.findById(order.id())).contains(order);
        assertThat(database.countOrders()).isEqualTo(1);
        assertThat(database.jdbc.sql("SELECT unit_price FROM orders WHERE id = :id")
                .param("id", order.id()).query(BigDecimal.class).single()).isEqualByComparingTo("19.99");
    }

    @Test
    @DisplayName("BU-R02 Repository maps a row inserted independently through SQL")
    void readsIndependentlyInsertedRow() {
        var order = order(UUID.randomUUID(), 1000, "99999999.99");
        database.jdbc.sql("""
                INSERT INTO orders (id, user_id, product, quantity, unit_price)
                VALUES (:id, :userId, :product, :quantity, :price)
                """)
                .param("id", order.id()).param("userId", order.userId())
                .param("product", order.product()).param("quantity", order.quantity())
                .param("price", order.unitPrice()).update();

        assertThat(database.orders.findById(order.id())).contains(order);
    }

    @Test
    @DisplayName("BU-R03 Unknown UUID produces an empty result")
    void returnsEmptyForUnknownIdentifier() {
        assertThat(database.orders.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("BU-R04 Duplicate primary key cannot overwrite the saved order")
    void duplicateIdentifierPreservesOriginalRow() {
        var original = order(UUID.randomUUID(), 2, "10.00");
        database.orders.save(original);

        assertThatThrownBy(() -> database.orders.save(order(original.id(), 5, "99.00")))
                .isInstanceOf(DuplicateKeyException.class);

        assertThat(database.countOrders()).isEqualTo(1);
        assertThat(database.orders.findById(original.id())).contains(original);
    }

    @ParameterizedTest(name = "BU-R05 H2 rejects quantity={0} even without the controller")
    @ValueSource(ints = {0, 1001})
    void databaseRejectsQuantityOutsideAllowedRange(int quantity) {
        assertThatThrownBy(() -> database.orders.save(order(UUID.randomUUID(), quantity, "10.00")))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(database.countOrders()).isZero();
    }

    @Test
    @DisplayName("BU-R06 H2 rejects zero price without leaving a row")
    void databaseRejectsZeroPrice() {
        assertThatThrownBy(() -> database.orders.save(order(UUID.randomUUID(), 1, "0.00")))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(database.countOrders()).isZero();
    }

    private Order order(UUID id, int quantity, String price) {
        return new Order(id, UserApiStub.USER_ID, "Клавиатура", quantity, new BigDecimal(price));
    }
}
