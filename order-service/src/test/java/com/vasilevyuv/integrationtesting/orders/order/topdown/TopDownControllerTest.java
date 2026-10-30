package com.vasilevyuv.integrationtesting.orders.order.topdown;

import com.vasilevyuv.integrationtesting.orders.order.api.OrderController;
import com.vasilevyuv.integrationtesting.orders.order.application.OrderNotFoundException;
import com.vasilevyuv.integrationtesting.orders.order.application.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static com.vasilevyuv.integrationtesting.orders.order.topdown.OrderTestData.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Stage 1: real MVC, validation and exception handling; application service is a stub.
@WebMvcTest(OrderController.class)
@Tag("topdown-controller")
class TopDownControllerTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private OrderService orders;

    @Test
    @DisplayName("TD-C01 POST maps input and returns 201 with Location")
    void createsOrderThroughService() throws Exception {
        when(orders.create(USER_ID, " Keyboard ", 2, PRICE)).thenReturn(DETAILS);

        mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(request(2)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/orders/" + ORDER_ID))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(ORDER_ID.toString()))
                .andExpect(jsonPath("$.total").value(599.80))
                .andExpect(jsonPath("$.user.id").value(USER_ID.toString()));

        verify(orders).create(USER_ID, " Keyboard ", 2, PRICE);
        verifyNoMoreInteractions(orders);
    }

    @Test
    @DisplayName("TD-C02 GET maps service result to order and user JSON")
    void readsOrderThroughService() throws Exception {
        when(orders.getById(ORDER_ID)).thenReturn(DETAILS);

        mvc.perform(get("/orders/{id}", ORDER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.product").value("Keyboard"))
                .andExpect(jsonPath("$.quantity").value(2))
                .andExpect(jsonPath("$.user.name").value("Anna Test"))
                .andExpect(jsonPath("$.user.email").value("anna@example.com"));

        verify(orders).getById(ORDER_ID);
    }

    @ParameterizedTest(name = "TD-C03 quantity={0} is rejected before service call")
    @ValueSource(ints = {0, 1001})
    void rejectsQuantityOutsideAllowedRange(int quantity) throws Exception {
        mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(request(quantity)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verifyNoInteractions(orders);
    }

    @Test
    @DisplayName("TD-C04 Fractional quantity is not silently rounded")
    void rejectsFractionalQuantity() throws Exception {
        var body = request(2).replace("\"quantity\":2", "\"quantity\":1.5");

        mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verifyNoInteractions(orders);
    }

    @Test
    @DisplayName("TD-C05 Malformed UUID is rejected before service call")
    void rejectsMalformedIdentifier() throws Exception {
        mvc.perform(get("/orders/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verifyNoInteractions(orders);
    }

    @Test
    @DisplayName("TD-C06 Missing order exception becomes HTTP 404")
    void mapsMissingOrderToNotFound() throws Exception {
        when(orders.getById(ORDER_ID)).thenThrow(new OrderNotFoundException(ORDER_ID));

        mvc.perform(get("/orders/{id}", ORDER_ID))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }
}
