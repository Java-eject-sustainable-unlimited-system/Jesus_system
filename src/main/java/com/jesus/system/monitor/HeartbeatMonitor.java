package com.jesus.system.monitor;

import com.jesus.system.config.WatchdogProperties;
import com.jesus.system.core.ProcessLifecycleManager;
import com.jesus.system.core.VitalsRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

/**
 * Detects a target that is alive but frozen: the target must touch the
 * configured heartbeat file periodically. A missing or stale file counts as a
 * failure. Only the file's modification time is read. Disabled when blank.
 */
@Component
public class HeartbeatMonitor {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatMonitor.class);
    private static final String COMPONENT = "heartbeat";

    private final WatchdogProperties properties;
    private final VitalsRegistry vitals;
    private final ProcessLifecycleManager lifecycleManager;
    private final FailureCounter failures = new FailureCounter();

    public HeartbeatMonitor(WatchdogProperties properties, VitalsRegistry vitals,
                            ProcessLifecycleManager lifecycleManager) {
        this.properties = properties;
        this.vitals = vitals;
        this.lifecycleManager = lifecycleManager;
    }

    @Scheduled(fixedRate = 2000)
    public void checkHeartbeat() {
        String file = properties.getHeartbeatFile();
        if (file == null || file.isBlank()) {
            return;
        }
        String problem = inspect(Path.of(file), Instant.now());
        if (problem == null) {
            if (failures.recoverOne() == 0) {
                vitals.reportUp(COMPONENT, "Heartbeat fresh");
            }
            return;
        }
        int count = failures.recordFailure();
        vitals.reportDown(COMPONENT, "%s (%d/%d)".formatted(problem, count, properties.getFailureThreshold()));
        if (failures.hasReached(properties.getFailureThreshold())) {
            log.warn("[💓 HEARTBEAT] {} — ejecting target.", problem);
            lifecycleManager.ejectAndResurrect("HEARTBEAT_STALE");
            failures.reset();
        }
    }

    private String inspect(Path path, Instant now) {
        try {
            Instant modified = Files.getLastModifiedTime(path).toInstant();
            Duration age = Duration.between(modified, now);
            return age.compareTo(properties.getHeartbeatMaxAge()) > 0
                    ? "Heartbeat stale for " + age.toSeconds() + "s" : null;
        } catch (java.io.IOException | RuntimeException e) {
            return "Heartbeat file unreadable";
        }
    }
}
