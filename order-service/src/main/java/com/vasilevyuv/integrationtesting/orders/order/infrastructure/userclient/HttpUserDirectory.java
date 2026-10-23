package com.vasilevyuv.integrationtesting.orders.order.infrastructure.userclient;

import com.vasilevyuv.integrationtesting.orders.order.application.UnknownUserException;
import com.vasilevyuv.integrationtesting.orders.order.application.UserDirectory;
import com.vasilevyuv.integrationtesting.orders.order.application.UserServiceUnavailableException;
import com.vasilevyuv.integrationtesting.orders.order.application.UserSummary;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.util.UUID;

@Component
public class HttpUserDirectory implements UserDirectory {
    private final RestClient client;

    public HttpUserDirectory(RestClient.Builder builder, UserClientProperties properties) {
        var httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        client = builder.baseUrl(properties.baseUrl().toString())
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public UserSummary getById(UUID id) {
        try {
            var user = client.get().uri("/users/{id}", id)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .onStatus(status -> status.value() == 404, (request, response) -> {
                        throw new UnknownUserException(id);
                    })
                    .body(UserSummary.class);
            if (user == null || !id.equals(user.id()) || user.name() == null || user.email() == null) {
                throw new UserServiceUnavailableException("Invalid response from user-service.");
            }
            return user;
        } catch (RestClientException exception) {
            throw new UserServiceUnavailableException(exception);
        }
    }
}
