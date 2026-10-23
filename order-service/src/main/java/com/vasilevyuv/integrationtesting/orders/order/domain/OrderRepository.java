package com.vasilevyuv.integrationtesting.orders.order.domain;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepository {
    void save(Order order);

    Optional<Order> findById(UUID id);
}
