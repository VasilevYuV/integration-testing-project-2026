package com.vasilevyuv.integrationtesting.support;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

/** Controlled HTTP responses for fault and delay scenarios; used only in tests. */
public final class UserServiceStub implements AutoCloseable {
    public static final UUID USER_ID = UUID.fromString("17e619f7-f485-4dcb-8628-182ea759032b");
    public static final String USER_JSON = """
            {"id":"%s","name":"Stub User","email":"stub@example.com"}
            """.formatted(USER_ID);

    private final HttpServer server;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private volatile Reply reply = new Reply(200, USER_JSON, Duration.ZERO);
    private volatile String lastPath;

    public UserServiceStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(executor);
        server.createContext("/users/", exchange -> {
            var current = reply;
            lastPath = exchange.getRequestURI().getPath();
            try (exchange) {
                Thread.sleep(current.delay());
                byte[] body = current.body().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(current.status(), body.length);
                exchange.getResponseBody().write(body);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (IOException exception) {
                // A read-timeout test deliberately disconnects before the delayed reply.
            }
        });
        server.start();
    }

    public void respond(int status, String body, Duration delay) {
        reply = new Reply(status, body, delay);
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public String lastPath() {
        return lastPath;
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }

    private record Reply(int status, String body, Duration delay) {
    }
}
