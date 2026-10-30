package com.vasilevyuv.integrationtesting.orders.order.topdown;

import com.vasilevyuv.integrationtesting.orders.order.api.OrderController;
import com.vasilevyuv.integrationtesting.orders.order.application.*;
import com.vasilevyuv.integrationtesting.orders.order.domain.Order;
import com.vasilevyuv.integrationtesting.orders.order.domain.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static com.vasilevyuv.integrationtesting.orders.order.topdown.OrderTestData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Stage 2: add the real application service; repository and user directory remain stubs.
@WebMvcTest(OrderController.class)
@Import(OrderService.class)
@Tag("topdown-service")
class TopDownServiceTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private OrderRepository orders;

    @MockitoBean
    private UserDirectory users;

    @Test
    @DisplayName("TD-S01 Create validates user, trims product and saves the calculated order")
    void createsOrderWithRealBusinessLogic() throws Exception {
        when(users.getById(USER_ID)).thenReturn(USER);

        var response = mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(request(2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.product").value("Keyboard"))
                .andExpect(jsonPath("$.total").value(599.80))
                .andReturn().getResponse();

        var saved = ArgumentCaptor.forClass(Order.class);
        var calls = inOrder(users, orders);
        calls.verify(users).getById(USER_ID);
        calls.verify(orders).save(saved.capture());
        assertThat(saved.getValue().id()).isNotNull();
        assertThat(saved.getValue().userId()).isEqualTo(USER_ID);
        assertThat(saved.getValue().product()).isEqualTo("Keyboard");
        assertThat(saved.getValue().quantity()).isEqualTo(2);
        assertThat(saved.getValue().unitPrice()).isEqualByComparingTo(PRICE);
        assertThat(saved.getValue().total()).isEqualByComparingTo("599.80");
        assertThat(response.getHeader("Location")).isEqualTo("/orders/" + saved.getValue().id());
    }

    @Test
    @DisplayName("TD-S02 Read joins saved order with current user data on every call")
    void readsCurrentUserDataInsteadOfCachingItInOrder() throws Exception {
        when(orders.findById(ORDER_ID)).thenReturn(Optional.of(ORDER));
        when(users.getById(USER_ID)).thenReturn(USER,
                new UserSummary(USER_ID, "Anna Updated", "updated@example.com"));

        mvc.perform(get("/orders/{id}", ORDER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value("anna@example.com"));
        mvc.perform(get("/orders/{id}", ORDER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ORDER_ID.toString()))
                .andExpect(jsonPath("$.total").value(599.80))
                .andExpect(jsonPath("$.user.name").value("Anna Updated"))
                .andExpect(jsonPath("$.user.email").value("updated@example.com"));

        verify(users, times(2)).getById(USER_ID);
        verify(orders, never()).save(any());
    }

    @Test
    @DisplayName("TD-S03 Missing order stops the chain before user lookup")
    void missingOrderDoesNotCallUsers() throws Exception {
        when(orders.findById(ORDER_ID)).thenReturn(Optional.empty());

        mvc.perform(get("/orders/{id}", ORDER_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));

        verifyNoInteractions(users);
        verify(orders, never()).save(any());
    }

    @Test
    @DisplayName("TD-S04 Unknown user returns 422 without saving an order")
    void unknownUserPreventsSave() throws Exception {
        when(users.getById(USER_ID)).thenThrow(new UnknownUserException(USER_ID));

        mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(request(2)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));

        verifyNoInteractions(orders);
    }

    @Test
    @DisplayName("TD-S05 User service failure returns 503 without saving an order")
    void unavailableUserServicePreventsSave() throws Exception {
        when(users.getById(USER_ID)).thenThrow(new UserServiceUnavailableException("Simulated outage"));

        mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(request(2)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("USER_SERVICE_UNAVAILABLE"));

        verifyNoInteractions(orders);
    }
}
