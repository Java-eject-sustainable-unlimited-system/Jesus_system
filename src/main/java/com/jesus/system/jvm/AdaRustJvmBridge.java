package com.jesus.system.jvm;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public final class AdaRustJvmBridge {

    private static final int MAX_BYTECODE_LENGTH = 65_536;
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    private final Path adaExecutable;
    private final Path rustExecutable;
    private final Duration timeout;

    public AdaRustJvmBridge(Path adaExecutable, Path rustExecutable) {
        this(adaExecutable, rustExecutable, DEFAULT_TIMEOUT);
    }

    public AdaRustJvmBridge(Path adaExecutable, Path rustExecutable, Duration timeout) {
        this.adaExecutable = Objects.requireNonNull(adaExecutable, "adaExecutable");
        this.rustExecutable = Objects.requireNonNull(rustExecutable, "rustExecutable");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
    }

    public static AdaRustJvmBridge fromEnvironment() {
        return new AdaRustJvmBridge(
                executableFromEnvironment("JESUS_ADA_VM"),
                executableFromEnvironment("JESUS_RUST_VM"));
    }

    public ExecutionResult executeAndCompare(byte[] bytecode) throws IOException, InterruptedException {
        Objects.requireNonNull(bytecode, "bytecode");
        if (bytecode.length == 0 || bytecode.length > MAX_BYTECODE_LENGTH) {
            throw new IllegalArgumentException("bytecode length must be between 1 and 65536 bytes");
        }

        Path input = Files.createTempFile("jesus-jvm-", ".code");
        try {
            Files.write(input, bytecode);
            int adaResult = execute(List.of(adaExecutable.toString(), input.toString()));
            int rustResult = execute(List.of(rustExecutable.toString(), input.toString()));
            if (adaResult != rustResult) {
                throw new IllegalStateException(
                        "Ada/Rust JVM mismatch: Ada=" + adaResult + ", Rust=" + rustResult);
            }
            return new ExecutionResult(adaResult, rustResult);
        } finally {
            Files.deleteIfExists(input);
        }
    }

    public int executeStaticClassMethod(Path classFile, String method, String descriptor)
            throws IOException, InterruptedException {
        Objects.requireNonNull(classFile, "classFile");
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(descriptor, "descriptor");
        return execute(List.of(
                adaExecutable.toString(),
                "--class-file",
                classFile.toAbsolutePath().toString(),
                method,
                descriptor));
    }

    private int execute(List<String> command) throws IOException, InterruptedException {
        Path output = Files.createTempFile("jesus-jvm-", ".out");
        try {
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(output.toFile())
                    .start();
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                process.waitFor();
                throw new IOException("JVM interpreter timed out");
            }

            String result = Files.readString(output).trim();
            if (process.exitValue() != 0) {
                throw new IOException("JVM interpreter failed: " + result);
            }
            try {
                return Integer.parseInt(result);
            } catch (NumberFormatException error) {
                throw new IOException("JVM interpreter returned an invalid integer: " + result, error);
            }
        } finally {
            Files.deleteIfExists(output);
        }
    }

    private static Path executableFromEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must point to the interpreter executable");
        }
        return Path.of(value);
    }

    public record ExecutionResult(int adaResult, int rustResult) {
    }
}
