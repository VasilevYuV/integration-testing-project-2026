package com.vasilevyuv.integrationtesting.orders.order.api;

import com.vasilevyuv.integrationtesting.orders.order.application.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/orders")
public class OrderController {
    private final OrderService orders;

    public OrderController(OrderService orders) {
        this.orders = orders;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
        var details = orders.create(request.userId(), request.product(), request.quantity(), request.unitPrice());
        return ResponseEntity.created(URI.create("/orders/" + details.order().id()))
                .body(OrderResponse.from(details));
    }

    @GetMapping("/{id}")
    public OrderResponse getById(@PathVariable UUID id) {
        return OrderResponse.from(orders.getById(id));
    }
}
