package com.jesus.system.api;

import com.jesus.system.config.WatchdogProperties;
import com.jesus.system.core.ProcessLifecycleManager;
import com.jesus.system.core.ProcessLifecycleManager.EjectionReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * Emergency manual override REST API.
 *
 * <p>Formerly {@code JesusCommandController}, which only logged the request
 * and returned a fake SUCCESS — it now actually executes the protocol and
 * returns the real ejection report.
 */
@RestController
@RequestMapping("/api/jesus")
public class WatchdogCommandController {

    private static final Logger log = LoggerFactory.getLogger(WatchdogCommandController.class);

    private final ProcessLifecycleManager lifecycleManager;
    private final WatchdogProperties properties;

    public WatchdogCommandController(ProcessLifecycleManager lifecycleManager, WatchdogProperties properties) {
        this.lifecycleManager = lifecycleManager;
        this.properties = properties;
    }

    @PostMapping("/eject-and-resurrect")
    public ResponseEntity<Map<String, Object>> manualEjectAndResurrect(
            @RequestHeader(name = "X-Watchdog-Token", required = false) String authorityToken) {
        if (!isAuthorized(authorityToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid watchdog control token");
        }

        log.warn("[⚠️ EMERGENCY] Authenticated manual ejection requested via API.");

        EjectionReport report = lifecycleManager.ejectAndResurrect("MANUAL_API_TRIGGER");

        if (!report.accepted()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "status", "IN_PROGRESS",
                    "action", "EJECT_AND_RESURRECT_SKIPPED",
                    "operationAccepted", false,
                    "processesTerminated", report.processesTerminated(),
                    "resurrectionTriggered", report.resurrectionTriggered(),
                    "timestamp", report.timestamp().toEpochMilli()
            ));
        }
        if (!report.resurrectionTriggered()) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", "FAILURE",
                    "action", "EJECT_AND_RESURRECT_FAILED",
                    "operationAccepted", true,
                    "processesTerminated", report.processesTerminated(),
                    "resurrectionTriggered", false,
                    "timestamp", report.timestamp().toEpochMilli()
            ));
        }

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "status", "STARTED",
                "action", "EJECT_AND_RESURRECT_STARTED",
                "operationAccepted", true,
                "processesTerminated", report.processesTerminated(),
                "resurrectionTriggered", report.resurrectionTriggered(),
                "timestamp", report.timestamp().toEpochMilli()
        ));
    }

    private boolean isAuthorized(String suppliedToken) {
        String configuredToken = properties.getManualControlToken();
        return suppliedToken != null
                && configuredToken != null
                && !configuredToken.isBlank()
                && MessageDigest.isEqual(
                        configuredToken.getBytes(StandardCharsets.UTF_8),
                        suppliedToken.getBytes(StandardCharsets.UTF_8));
    }
}
