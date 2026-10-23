package com.vasilevyuv.integrationtesting.orders.order.application;

import com.vasilevyuv.integrationtesting.orders.order.domain.Order;
import com.vasilevyuv.integrationtesting.orders.order.domain.OrderRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class OrderService {
    private final OrderRepository orders;
    private final UserDirectory users;

    public OrderService(OrderRepository orders, UserDirectory users) {
        this.orders = orders;
        this.users = users;
    }

    public OrderDetails create(UUID userId, String product, int quantity, BigDecimal unitPrice) {
        var user = users.getById(userId);
        var order = new Order(UUID.randomUUID(), userId, product.strip(), quantity, unitPrice);
        orders.save(order);
        return new OrderDetails(order, user);
    }

    public OrderDetails getById(UUID id) {
        var order = orders.findById(id).orElseThrow(() -> new OrderNotFoundException(id));
        return new OrderDetails(order, users.getById(order.userId()));
    }
}
