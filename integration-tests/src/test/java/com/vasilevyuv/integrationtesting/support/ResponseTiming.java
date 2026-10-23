package com.vasilevyuv.integrationtesting.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;

public final class ResponseTiming {
    private ResponseTiming() {
    }

    public static long record(String scenario, long startedAt) throws IOException {
        long elapsedMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
        var output = Path.of("target", "response-times.csv");
        if (Files.notExists(output)) {
            Files.writeString(output, "scenario,elapsed_ms\n");
        }
        Files.writeString(output, scenario + "," + elapsedMs + "\n", StandardOpenOption.APPEND);
        System.out.printf("Response time: %s = %d ms%n", scenario, elapsedMs);
        return elapsedMs;
    }
}
