package com.vasilevyuv.integrationtesting.orders.order.hybrid;

import com.vasilevyuv.integrationtesting.orders.order.api.OrderController;
import com.vasilevyuv.integrationtesting.orders.order.application.OrderService;
import com.vasilevyuv.integrationtesting.orders.order.application.UserServiceUnavailableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import static com.vasilevyuv.integrationtesting.orders.order.hybrid.HybridOrderCases.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Upper branch: the API contract is checked before connecting the application service.
@WebMvcTest(OrderController.class)
@Tag("hybrid-api-contract")
class HybridApiContractTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private OrderService orders;

    @ParameterizedTest(name = "HY-U01 [{index}] API contract: {0}")
    @MethodSource("com.vasilevyuv.integrationtesting.orders.order.hybrid.HybridOrderCases#validOrders")
    void mapsBoundaryValuesWithoutLosingPrecision(OrderCase sample) throws Exception {
        when(orders.create(sample.userId(), sample.inputProduct(), sample.quantity(), sample.price()))
                .thenReturn(sample.details());

        var response = mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON)
                        .content(sample.requestJson()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/orders/" + sample.orderId()))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.product").value(sample.storedProduct()))
                .andExpect(jsonPath("$.quantity").value(sample.quantity()))
                .andExpect(jsonPath("$.user.id").value(sample.userId().toString()))
                .andReturn().getResponse();
        var body = JsonMapper.builder().build().readTree(response.getContentAsByteArray());
        assertThat(body.get("unitPrice").decimalValue()).isEqualByComparingTo(sample.price());
        assertThat(body.get("total").decimalValue()).isEqualByComparingTo(sample.total());
        verify(orders).create(sample.userId(), sample.inputProduct(), sample.quantity(), sample.price());
        verifyNoMoreInteractions(orders);
    }

    @Test
    @DisplayName("HY-U02 Excess price precision is rejected before the service")
    void rejectsExcessPricePrecision() throws Exception {
        mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON)
                        .content(MINIMUM.requestJson().replace("0.01", "0.019")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(orders);
    }

    @Test
    @DisplayName("HY-U03 Dependency failure maps to the public 503 contract")
    void mapsDependencyFailure() throws Exception {
        when(orders.create(MINIMUM.userId(), MINIMUM.inputProduct(), MINIMUM.quantity(), MINIMUM.price()))
                .thenThrow(new UserServiceUnavailableException("User lookup timed out"));

        mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(MINIMUM.requestJson()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.code").value("USER_SERVICE_UNAVAILABLE"));
    }
}
