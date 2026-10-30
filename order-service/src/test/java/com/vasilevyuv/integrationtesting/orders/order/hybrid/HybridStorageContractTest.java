package com.vasilevyuv.integrationtesting.orders.order.hybrid;

import com.vasilevyuv.integrationtesting.orders.order.support.OrderDatabase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static com.vasilevyuv.integrationtesting.orders.order.hybrid.HybridOrderCases.*;
import static org.assertj.core.api.Assertions.assertThat;

// Lower branch: the same contract is checked against JDBC and the production schema.
@Tag("hybrid-storage-contract")
class HybridStorageContractTest {
    @ParameterizedTest(name = "HY-L01 [{index}] Storage contract: {0}")
    @MethodSource("com.vasilevyuv.integrationtesting.orders.order.hybrid.HybridOrderCases#validOrders")
    void storesBoundaryValuesWithoutLosingPrecision(OrderCase sample) {
        try (var database = new OrderDatabase()) {
            database.orders.save(sample.order(sample.orderId()));

            var saved = database.orders.findById(sample.orderId()).orElseThrow();
            assertThat(saved).isEqualTo(sample.order(sample.orderId()));
            assertThat(saved.total()).isEqualByComparingTo(sample.total());
            assertThat(database.jdbc.sql("SELECT product FROM orders WHERE id = :id")
                    .param("id", sample.orderId()).query(String.class).single()).isEqualTo(sample.storedProduct());
            assertThat(database.countOrders()).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("HY-L02 Rows for different orders and users remain independent")
    void keepsOrdersSeparate() {
        try (var database = new OrderDatabase()) {
            database.orders.save(MINIMUM.order(MINIMUM.orderId()));
            database.orders.save(MAXIMUM.order(MAXIMUM.orderId()));

            assertThat(database.orders.findById(MINIMUM.orderId())).contains(MINIMUM.order(MINIMUM.orderId()));
            assertThat(database.orders.findById(MAXIMUM.orderId())).contains(MAXIMUM.order(MAXIMUM.orderId()));
            assertThat(database.countOrders()).isEqualTo(2);
        }
    }
}
