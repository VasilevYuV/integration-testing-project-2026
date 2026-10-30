package com.vasilevyuv.integrationtesting.orders.order.support;

import com.vasilevyuv.integrationtesting.orders.order.infrastructure.persistence.JdbcOrderRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

/** A fresh H2 database with the application's schema, without an application context. */
public final class OrderDatabase implements AutoCloseable {
    private final EmbeddedDatabase database = new EmbeddedDatabaseBuilder()
            .generateUniqueName(true)
            .setType(EmbeddedDatabaseType.H2)
            .addScript("classpath:schema.sql")
            .build();

    public final JdbcClient jdbc = JdbcClient.create(database);
    public final JdbcOrderRepository orders = new JdbcOrderRepository(jdbc);

    public long countOrders() {
        return jdbc.sql("SELECT COUNT(*) FROM orders").query(Long.class).single();
    }

    @Override
    public void close() {
        database.shutdown();
    }
}
