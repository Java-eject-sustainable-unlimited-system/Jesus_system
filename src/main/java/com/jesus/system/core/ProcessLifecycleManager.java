package com.jesus.system.core;

import com.jesus.system.config.WatchdogProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Single place that knows how to eject (SIGKILL) a frozen legacy target
 * and resurrect it as a pristine Nix artifact.
 *
 * <p>Replaces the eject/resurrect logic previously copy-pasted across
 * every watchdog service, and guards against concurrent monitors stacking
 * duplicate resurrections.
 */
@Component
public class ProcessLifecycleManager {

    private static final Logger log = LoggerFactory.getLogger(ProcessLifecycleManager.class);

    private static final Duration EXIT_WAIT = Duration.ofSeconds(5);

    private final WatchdogProperties properties;
    private final ProcessStarter processStarter;
    private final AtomicBoolean resurrectionGuard = new AtomicBoolean(false);

    @Autowired
    public ProcessLifecycleManager(WatchdogProperties properties) {
        this(properties, target -> new ProcessBuilder(launchCommand(properties, target))
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start());
    }

    static List<String> launchCommand(WatchdogProperties properties, String nixTarget) {
        List<String> custom = properties.getRestartCommand();
        if (custom != null && !custom.isEmpty()) {
            return custom;
        }
        return List.of("nix", "run", nixTarget);
    }

    ProcessLifecycleManager(WatchdogProperties properties, ProcessStarter processStarter) {
        this.properties = properties;
        this.processStarter = processStarter;
    }

    /**
     * Full J.E.S.U.S protocol: terminate every matching target process, then respawn via Nix.
     */
    public EjectionReport ejectAndResurrect(String reason) {
        int terminated = ejectTargetProcesses(reason);
        boolean respawned = resurrect();
        return new EjectionReport(reason, terminated, respawned, Instant.now());
    }

    /**
     * Forcibly terminate all target legacy processes.
     *
     * @return number of processes terminated
     */
    public int ejectTargetProcesses(String reason) {
        List<ProcessHandle> targets = findTargetProcesses();
        int terminated = 0;
        for (ProcessHandle process : targets) {
            log.warn("[🗡️ EJECT] Terminating PID {} [reason: {}]", process.pid(), reason);
            try {
                process.destroyForcibly();
                process.onExit().get(EXIT_WAIT.toMillis(), TimeUnit.MILLISECONDS);
                terminated++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("[🗡️ EJECT] Interrupted while waiting for PID {} to exit.", process.pid());
                break;
            } catch (TimeoutException | ExecutionException | RuntimeException e) {
                log.error("[💥] PID {} did not confirm termination: {}", process.pid(), e.getMessage());
            }
        }
        return terminated;
    }

    /**
     * Respawn the target through the Nix flake. Guarded until the launched
     * process exits so simultaneous triggers cannot stack duplicate instances.
     *
     * @return true if a respawn was actually triggered
     */
    public boolean resurrect() {
        if (!resurrectionGuard.compareAndSet(false, true)) {
            log.info("[⚡ RESURRECT] Resurrection already in progress, skipping duplicate trigger.");
            return false;
        }
        try {
            log.info("[⚡ RESURRECT] Spawning fresh instance via Nix: {}", properties.getNixRunTarget());
            Process process = processStarter.start(properties.getNixRunTarget());
            process.onExit().whenComplete((finished, failure) -> resurrectionGuard.set(false));
            log.info("[✨] Resurrection trigger dispatched successfully.");
            return true;
        } catch (IOException | RuntimeException e) {
            resurrectionGuard.set(false);
            log.error("[💥 CRITICAL] Failed to resurrect target via Nix: {}", e.getMessage());
            return false;
        }
    }

    public List<ProcessHandle> findTargetProcesses() {
        try (var processes = ProcessHandle.allProcesses()) {
            return processes
                    .filter(this::isTargetProcess)
                    .toList();
        }
    }

    private boolean isTargetProcess(ProcessHandle process) {
        // Defensive: the watchdog must never kill its own JVM.
        if (process.pid() == ProcessHandle.current().pid()) {
            return false;
        }
        // Only terminate processes owned by the same OS user as the watchdog.
        String owner = process.info().user().orElse(null);
        String self = ProcessHandle.current().info().user().orElse(null);
        if (owner == null || !owner.equals(self)) {
            return false;
        }
        return matchesTarget(
                process.info().command().orElse(""),
                process.info().arguments().orElse(new String[0]),
                properties.getProcessCommandKeyword(),
                properties.getTargetProcessKeywords());
    }

    /**
     * Pure, package-visible matcher so the process targeting rules are
     * unit-testable without touching real system processes.
     */
    static boolean matchesTarget(String command, String[] arguments,
                                 String commandKeyword, List<String> targetKeywords) {
        if (commandKeyword == null || commandKeyword.isBlank() || targetKeywords == null) {
            return false;
        }

        String normalizedCommand = (command == null ? "" : command).toLowerCase(Locale.ROOT);
        String normalizedArguments = Arrays.stream(arguments == null ? new String[0] : arguments)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.joining(" "))
                .toLowerCase(Locale.ROOT);
        String keyword = commandKeyword.trim().toLowerCase(Locale.ROOT);
        String executable = normalizedCommand.substring(
                Math.max(normalizedCommand.lastIndexOf('/'), normalizedCommand.lastIndexOf('\\')) + 1);
        return !keyword.isBlank()
                && (executable.equals(keyword) || executable.equals(keyword + ".exe"))
                && targetKeywords.stream()
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(targetKeyword -> !targetKeyword.isEmpty())
                        .map(targetKeyword -> targetKeyword.toLowerCase(Locale.ROOT))
                        .anyMatch(normalizedArguments::contains);
    }

    public record EjectionReport(
            String reason,
            int processesTerminated,
            boolean resurrectionTriggered,
            Instant timestamp) {
    }

    @FunctionalInterface
    interface ProcessStarter {
        Process start(String nixRunTarget) throws IOException;
    }
}
