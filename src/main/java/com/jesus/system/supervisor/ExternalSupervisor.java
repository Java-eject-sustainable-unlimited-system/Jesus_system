package com.jesus.system.supervisor;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * Out-of-JVM supervisor: a tiny plain-Java process (no Spring) that runs
 * independently of both the target app and the Jesus watchdog JVM.
 *
 * <ul>
 *   <li>Target JVM down/hung (TCP connect fails or times out): runs the target recovery command.</li>
 *   <li>Watchdog JVM down/hung (heartbeat HTTP check fails or times out): runs the watchdog restart command.</li>
 * </ul>
 *
 * <p>The supervisor itself is kept alive by the OS service manager
 * (systemd on Linux, launchd on macOS); see {@code deploy/}.
 *
 * <p>Configuration (environment variables):
 * {@code SUPERVISOR_TARGET_HOST}, {@code SUPERVISOR_TARGET_PORT},
 * {@code SUPERVISOR_WATCHDOG_HEARTBEAT_URL} (loopback only),
 * {@code SUPERVISOR_INTERVAL_MS}, {@code SUPERVISOR_TIMEOUT_MS}, {@code SUPERVISOR_FAILURE_THRESHOLD},
 * {@code SUPERVISOR_TARGET_RECOVERY_CMD}, {@code SUPERVISOR_WATCHDOG_RESTART_CMD}
 * (whitespace-separated command; empty disables that action).
 */
public final class ExternalSupervisor {

    private final BooleanSupplier targetCheck;
    private final BooleanSupplier watchdogCheck;
    private final Runnable targetRecovery;
    private final Runnable watchdogRestart;
    private final int threshold;
    private int targetFailures;
    private int watchdogFailures;

    ExternalSupervisor(BooleanSupplier targetCheck, BooleanSupplier watchdogCheck,
                       Runnable targetRecovery, Runnable watchdogRestart, int threshold) {
        this.targetCheck = targetCheck;
        this.watchdogCheck = watchdogCheck;
        this.targetRecovery = targetRecovery;
        this.watchdogRestart = watchdogRestart;
        this.threshold = Math.max(1, threshold);
    }

    /** One supervision cycle. */
    void tick() {
        if (targetCheck.getAsBoolean()) {
            targetFailures = 0;
        } else if (++targetFailures >= threshold) {
            targetFailures = 0;
            targetRecovery.run();
        }
        if (watchdogCheck.getAsBoolean()) {
            watchdogFailures = 0;
        } else if (++watchdogFailures >= threshold) {
            watchdogFailures = 0;
            watchdogRestart.run();
        }
    }

    static boolean tcpAlive(String host, int port, Duration timeout) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), (int) timeout.toMillis());
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    static boolean heartbeatAlive(HttpClient client, URI uri, Duration timeout) {
        try {
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(timeout).GET().build();
            return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    static Runnable command(String name, String commandLine, Duration timeout) {
        if (commandLine == null || commandLine.isBlank()) {
            return () -> log(name + " action disabled (no command configured)");
        }
        List<String> argv = Arrays.asList(commandLine.trim().split("\\s+"));
        return () -> {
            log(name + ": running " + argv);
            try {
                Process p = new ProcessBuilder(argv)
                        .redirectErrorStream(true)
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .start();
                if (!p.waitFor(timeout.toMillis() * 10, TimeUnit.MILLISECONDS)) {
                    p.destroyForcibly();
                    log(name + ": command timed out");
                }
            } catch (IOException e) {
                log(name + ": failed to run command: " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
    }

    private static void log(String message) {
        System.err.println("[jesus-supervisor] " + message);
    }

    private static String env(String key, String def) {
        String v = System.getenv(key);
        return v == null ? def : v;
    }

    public static void main(String[] args) throws InterruptedException {
        String host = env("SUPERVISOR_TARGET_HOST", "127.0.0.1");
        int port = Integer.parseInt(env("SUPERVISOR_TARGET_PORT", "8080"));
        URI heartbeat = URI.create(env("SUPERVISOR_WATCHDOG_HEARTBEAT_URL", "http://127.0.0.1:9090/actuator/health"));
        long interval = Long.parseLong(env("SUPERVISOR_INTERVAL_MS", "2000"));
        Duration timeout = Duration.ofMillis(Long.parseLong(env("SUPERVISOR_TIMEOUT_MS", "1500")));
        int threshold = Integer.parseInt(env("SUPERVISOR_FAILURE_THRESHOLD", "3"));

        String hbHost = heartbeat.getHost();
        if (!"127.0.0.1".equals(hbHost) && !"localhost".equals(hbHost) && !"[::1]".equals(hbHost)) {
            throw new IllegalArgumentException("Heartbeat URL must be loopback: " + heartbeat);
        }

        HttpClient client = HttpClient.newBuilder().connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NEVER).build();
        ExternalSupervisor supervisor = new ExternalSupervisor(
                () -> tcpAlive(host, port, timeout),
                () -> heartbeatAlive(client, heartbeat, timeout),
                command("target-recovery", env("SUPERVISOR_TARGET_RECOVERY_CMD", "nix run .#jesus-server-17"), timeout),
                command("watchdog-restart", env("SUPERVISOR_WATCHDOG_RESTART_CMD", ""), timeout),
                threshold);
        log("started: target=" + host + ":" + port + " heartbeat=" + heartbeat);
        while (!Thread.currentThread().isInterrupted()) {
            supervisor.tick();
            Thread.sleep(interval);
        }
    }
}
