package com.vasilevyuv.integrationtesting.orders.order.application;

import java.util.UUID;

public record UserSummary(UUID id, String name, String email) {
}
