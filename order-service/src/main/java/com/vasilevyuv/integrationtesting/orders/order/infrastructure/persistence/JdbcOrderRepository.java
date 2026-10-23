package com.vasilevyuv.integrationtesting.orders.order.infrastructure.persistence;

import com.vasilevyuv.integrationtesting.orders.order.domain.Order;
import com.vasilevyuv.integrationtesting.orders.order.domain.OrderRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcOrderRepository implements OrderRepository {
    private final JdbcClient jdbc;

    public JdbcOrderRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(Order order) {
        jdbc.sql("""
                INSERT INTO orders (id, user_id, product, quantity, unit_price)
                VALUES (:id, :userId, :product, :quantity, :unitPrice)
                """)
                .param("id", order.id())
                .param("userId", order.userId())
                .param("product", order.product())
                .param("quantity", order.quantity())
                .param("unitPrice", order.unitPrice())
                .update();
    }

    @Override
    public Optional<Order> findById(UUID id) {
        return jdbc.sql("SELECT id, user_id, product, quantity, unit_price FROM orders WHERE id = :id")
                .param("id", id)
                .query((row, rowNumber) -> new Order(row.getObject("id", UUID.class),
                        row.getObject("user_id", UUID.class), row.getString("product"),
                        row.getInt("quantity"), row.getBigDecimal("unit_price")))
                .optional();
    }
}
