package com.vasilevyuv.integrationtesting.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public final class RunningService implements AutoCloseable {
    private static final Pattern PORT = Pattern.compile("Tomcat started on port (\\d+)");
    private static final Duration START_TIMEOUT = Duration.ofSeconds(45);

    private final Process process;
    private final String baseUrl;

    private RunningService(Process process, int port) {
        this.process = process;
        this.baseUrl = "http://127.0.0.1:" + port;
    }

    public static RunningService start(Path jar, String... arguments) throws IOException, InterruptedException {
        if (!Files.isRegularFile(jar)) {
            throw new IllegalArgumentException("Build the service JAR first: " + jar);
        }
        var logs = Files.createDirectories(Path.of("target", "service-logs"));
        var log = Files.createTempFile(logs, jar.getFileName().toString(), ".log");
        var java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        var command = new ArrayList<>(List.of(java, "-Dfile.encoding=UTF-8", "-jar", jar.toString(),
                "--server.address=127.0.0.1", "--server.port=0", "--spring.main.banner-mode=off"));
        command.addAll(List.of(arguments));
        var process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            var deadline = System.nanoTime() + START_TIMEOUT.toNanos();
            while (System.nanoTime() < deadline) {
                var output = Files.readString(log);
                var matcher = PORT.matcher(output);
                if (process.isAlive() && output.contains("Started ") && matcher.find()) {
                    return new RunningService(process, Integer.parseInt(matcher.group(1)));
                }
                if (process.waitFor(100, TimeUnit.MILLISECONDS)) {
                    throw new IOException("Service exited before startup. " + log + "\n" + Files.readString(log));
                }
            }
            throw new IOException("Service startup timed out. " + log + "\n" + Files.readString(log));
        } catch (IOException | InterruptedException | RuntimeException exception) {
            process.destroyForcibly();
            throw exception;
        }
    }

    public String baseUrl() {
        return baseUrl;
    }

    @Override
    public void close() throws InterruptedException {
        process.destroy();
        if (!process.waitFor(5, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Test service did not stop: " + process.pid());
            }
        }
    }
}
