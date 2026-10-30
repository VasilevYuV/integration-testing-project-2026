package com.vasilevyuv.integrationtesting.orders.order.support;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Replaces only the external users application; requests still cross a real HTTP connection. */
public final class UserApiStub implements AutoCloseable {
    public static final UUID USER_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
    public static final String USER_JSON = """
            {"id":"%s","name":"Anna Test","email":"anna@example.com"}
            """.formatted(USER_ID);

    private final HttpServer server;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private volatile Reply reply = new Reply(200, USER_JSON, Duration.ZERO, new CompletableFuture<>());

    public UserApiStub() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        server.createContext("/users/", exchange -> {
            requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
            var current = reply;
            try (exchange) {
                Thread.sleep(current.delay());
                byte[] body = current.body().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(current.status(), body.length);
                exchange.getResponseBody().write(body);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (IOException exception) {
                // A timed-out client may close the connection before the delayed reply.
            } finally {
                current.finished().complete(null);
            }
        });
        server.setExecutor(executor);
        server.start();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public void respond(int status, String body) {
        respondAfter(status, body, Duration.ZERO);
    }

    /** Completes after the response attempt, including a client disconnect after a timeout. */
    public CompletableFuture<Void> respondAfter(int status, String body, Duration delay) {
        var finished = new CompletableFuture<Void>();
        reply = new Reply(status, body, delay, finished);
        return finished;
    }

    public void reset() {
        requests.clear();
        respond(200, USER_JSON);
    }

    public List<String> requests() {
        return List.copyOf(requests);
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
        executor.close();
    }

    private record Reply(int status, String body, Duration delay, CompletableFuture<Void> finished) {
    }
}
