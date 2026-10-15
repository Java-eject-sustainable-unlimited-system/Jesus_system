package com.jesus.system.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Externalized configuration for the J.E.S.U.S watchdog.
 * Bound from the {@code jesus.watchdog} prefix in application.yml.
 */
@ConfigurationProperties(prefix = "jesus.watchdog")
public class WatchdogProperties {

    /** Exact executable path, or executable basename (e.g. "java"), identifying a JVM process. */
    private String processCommandKeyword = "java";

    /** Exact command-line arguments identifying monitored legacy target processes. */
    private List<String> targetProcessKeywords = new ArrayList<>(List.of("egov", "tomcat"));

    /** Nix flake app used to respawn the target after ejection. */
    private String nixRunTarget = ".#jesus-server-17";

    /** Shared token required by the destructive manual-control endpoint. */
    private String manualControlToken = "";

    /** Actuator health endpoint of the monitored target. */
    private String healthEndpoint = "http://localhost:8080/actuator/health";

    /** Origins the HTTP vitals probe is allowed to contact. */
    private List<String> healthEndpointAllowedOrigins =
            new ArrayList<>(List.of("http://localhost:8080"));

    /** Host of the monitored target for the raw TCP probe. */
    private String probeHost = "127.0.0.1";

    /** Port of the monitored target for the raw TCP probe. */
    private int probePort = 8080;

    /** TCP connect timeout for the raw socket probe. */
    private Duration probeTimeout = Duration.ofMillis(300);

    /** HTTP connect/read timeout for vitals checks. */
    private Duration httpTimeout = Duration.ofMillis(1500);

    /** Consecutive health check failures required before ejection. */
    private int failureThreshold = 3;

    /** Consecutive I/O failures required before ejection. */
    private int ioFailureThreshold = 2;

    /** Heap usage ratio treated as critical (0.92 = 92%). */
    private double heapCriticalThreshold = 0.92;

    /** Minimum free disk space before emergency ejection. */
    private DataSize minFreeDiskSpace = DataSize.ofMegabytes(500);

    public String getProcessCommandKeyword() {
        return processCommandKeyword;
    }

    public void setProcessCommandKeyword(String processCommandKeyword) {
        this.processCommandKeyword = processCommandKeyword;
    }

    public List<String> getTargetProcessKeywords() {
        return targetProcessKeywords;
    }

    public void setTargetProcessKeywords(List<String> targetProcessKeywords) {
        this.targetProcessKeywords = targetProcessKeywords;
    }

    public String getNixRunTarget() {
        return nixRunTarget;
    }

    public void setNixRunTarget(String nixRunTarget) {
        if (nixRunTarget == null || nixRunTarget.isBlank()) {
            throw new IllegalArgumentException("nixRunTarget must not be blank");
        }
        this.nixRunTarget = nixRunTarget.trim();
    }

    public String getManualControlToken() {
        return manualControlToken;
    }

    public void setManualControlToken(String manualControlToken) {
        this.manualControlToken = manualControlToken;
    }

    public String getHealthEndpoint() {
        return healthEndpoint;
    }

    public void setHealthEndpoint(String healthEndpoint) {
        this.healthEndpoint = healthEndpoint;
    }

    public List<String> getHealthEndpointAllowedOrigins() {
        return healthEndpointAllowedOrigins;
    }

    public void setHealthEndpointAllowedOrigins(List<String> healthEndpointAllowedOrigins) {
        this.healthEndpointAllowedOrigins = healthEndpointAllowedOrigins;
    }

    public String getProbeHost() {
        return probeHost;
    }

    public void setProbeHost(String probeHost) {
        if (probeHost == null || probeHost.isBlank()) {
            throw new IllegalArgumentException("probeHost must not be blank");
        }
        this.probeHost = probeHost.trim();
    }

    public int getProbePort() {
        return probePort;
    }

    public void setProbePort(int probePort) {
        if (probePort < 1 || probePort > 65535) {
            throw new IllegalArgumentException("probePort must be between 1 and 65535");
        }
        this.probePort = probePort;
    }

    public Duration getProbeTimeout() {
        return probeTimeout;
    }

    public void setProbeTimeout(Duration probeTimeout) {
        requirePositiveDuration(probeTimeout, "probeTimeout");
        if (probeTimeout.compareTo(Duration.ofMillis(Integer.MAX_VALUE)) > 0
                || probeTimeout.toMillis() < 1) {
            throw new IllegalArgumentException("probeTimeout must not exceed " + Integer.MAX_VALUE + " milliseconds");
        }
        this.probeTimeout = probeTimeout;
    }

    public Duration getHttpTimeout() {
        return httpTimeout;
    }

    public void setHttpTimeout(Duration httpTimeout) {
        requirePositiveDuration(httpTimeout, "httpTimeout");
        this.httpTimeout = httpTimeout;
    }

    public int getFailureThreshold() {
        return failureThreshold;
    }

    public void setFailureThreshold(int failureThreshold) {
        if (failureThreshold < 1) {
            throw new IllegalArgumentException("failureThreshold must be positive");
        }
        this.failureThreshold = failureThreshold;
    }

    public int getIoFailureThreshold() {
        return ioFailureThreshold;
    }

    public void setIoFailureThreshold(int ioFailureThreshold) {
        if (ioFailureThreshold < 1) {
            throw new IllegalArgumentException("ioFailureThreshold must be positive");
        }
        this.ioFailureThreshold = ioFailureThreshold;
    }

    public double getHeapCriticalThreshold() {
        return heapCriticalThreshold;
    }

    public void setHeapCriticalThreshold(double heapCriticalThreshold) {
        if (!Double.isFinite(heapCriticalThreshold)
                || heapCriticalThreshold <= 0.0
                || heapCriticalThreshold > 1.0) {
            throw new IllegalArgumentException("heapCriticalThreshold must be finite and in the range (0, 1]");
        }
        this.heapCriticalThreshold = heapCriticalThreshold;
    }

    public DataSize getMinFreeDiskSpace() {
        return minFreeDiskSpace;
    }

    public void setMinFreeDiskSpace(DataSize minFreeDiskSpace) {
        if (minFreeDiskSpace == null || minFreeDiskSpace.toBytes() < 0) {
            throw new IllegalArgumentException("minFreeDiskSpace must not be negative or null");
        }
        this.minFreeDiskSpace = minFreeDiskSpace;
    }

    private static void requirePositiveDuration(Duration duration, String propertyName) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(propertyName + " must be positive");
        }
    }
}
