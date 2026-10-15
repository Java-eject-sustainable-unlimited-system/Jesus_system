package com.jesus.system.core;

import com.jesus.system.config.WatchdogProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
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
    private final Clock clock;
    private final AtomicBoolean resurrectionGuard = new AtomicBoolean(false);
    private final Deque<Instant> recoveryHistory = new ArrayDeque<>();

    @Autowired
    public ProcessLifecycleManager(WatchdogProperties properties) {
        this(properties, target -> new ProcessBuilder("nix", "run", target)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start(), Clock.systemUTC());
    }

    ProcessLifecycleManager(WatchdogProperties properties, ProcessStarter processStarter) {
        this(properties, processStarter, Clock.systemUTC());
    }

    ProcessLifecycleManager(WatchdogProperties properties, ProcessStarter processStarter, Clock clock) {
        this.properties = properties;
        this.processStarter = processStarter;
        this.clock = clock;
    }

    /**
     * Full J.E.S.U.S protocol: terminate every matching target process, then respawn via Nix.
     */
    public EjectionReport ejectAndResurrect(String reason) {
        Instant now = clock.instant();
        if (!tryAcquireRecoverySlot(now)) {
            log.warn("[🛑 THROTTLED] Recovery suppressed [reason: {}]: cooldown or per-window limit reached.", reason);
            return new EjectionReport(reason, 0, false, now, true);
        }
        List<ProcessHandle> targets = findTargetProcesses();
        if (targets.size() > properties.getMaxTargetProcesses()) {
            log.error("[🛑 REFUSED] {} processes match the target keywords (limit {}); keywords too broad, nothing killed [reason: {}].",
                    targets.size(), properties.getMaxTargetProcesses(), reason);
            return new EjectionReport(reason, 0, false, now, true);
        }
        if (properties.isDryRun()) {
            log.warn("[🧪 DRY-RUN] Would terminate {} process(es) and respawn via Nix [reason: {}].",
                    targets.size(), reason);
            return new EjectionReport(reason, 0, false, now, false);
        }
        int terminated = terminate(targets, reason);
        boolean respawned = resurrect();
        return new EjectionReport(reason, terminated, respawned, now, false);
    }

    /**
     * Records a recovery attempt unless the cooldown has not elapsed or the
     * per-window limit is exhausted, so false positives cannot cause restart loops.
     */
    private synchronized boolean tryAcquireRecoverySlot(Instant now) {
        Instant windowStart = now.minus(properties.getRecoveryWindow());
        while (!recoveryHistory.isEmpty() && !recoveryHistory.peekFirst().isAfter(windowStart)) {
            recoveryHistory.removeFirst();
        }
        Instant last = recoveryHistory.peekLast();
        if (last != null && now.isBefore(last.plus(properties.getRecoveryCooldown()))) {
            return false;
        }
        if (recoveryHistory.size() >= properties.getMaxRecoveriesPerWindow()) {
            return false;
        }
        recoveryHistory.addLast(now);
        return true;
    }

    /**
     * Forcibly terminate all target legacy processes.
     *
     * @return number of processes terminated
     */
    public int ejectTargetProcesses(String reason) {
        return terminate(findTargetProcesses(), reason);
    }

    private int terminate(List<ProcessHandle> targets, String reason) {
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
            Instant timestamp,
            boolean throttled) {

        public EjectionReport(String reason, int processesTerminated,
                              boolean resurrectionTriggered, Instant timestamp) {
            this(reason, processesTerminated, resurrectionTriggered, timestamp, false);
        }
    }

    @FunctionalInterface
    interface ProcessStarter {
        Process start(String nixRunTarget) throws IOException;
    }
}
