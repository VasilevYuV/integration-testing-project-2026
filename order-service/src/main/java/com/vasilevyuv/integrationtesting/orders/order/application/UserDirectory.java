package com.vasilevyuv.integrationtesting.orders.order.application;

import java.util.UUID;

public interface UserDirectory {
    UserSummary getById(UUID id);
}
