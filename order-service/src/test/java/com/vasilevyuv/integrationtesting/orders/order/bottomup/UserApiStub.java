package com.vasilevyuv.integrationtesting.orders.order.bottomup;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/** Replaces only the external users application; requests still cross a real HTTP connection. */
final class UserApiStub implements AutoCloseable {
    static final UUID USER_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
    static final String USER_JSON = """
            {"id":"%s","name":"Anna Test","email":"anna@example.com"}
            """.formatted(USER_ID);

    private final HttpServer server;
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private volatile Reply reply = new Reply(200, USER_JSON);

    UserApiStub() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        server.createContext("/users/", exchange -> {
            requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
            var current = reply;
            try (exchange) {
                byte[] body = current.body().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(current.status(), body.length);
                exchange.getResponseBody().write(body);
            }
        });
        server.start();
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    void respond(int status, String body) {
        reply = new Reply(status, body);
    }

    void reset() {
        requests.clear();
        reply = new Reply(200, USER_JSON);
    }

    List<String> requests() {
        return List.copyOf(requests);
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private record Reply(int status, String body) {
    }
}
