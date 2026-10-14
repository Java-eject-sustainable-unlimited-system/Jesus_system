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

    /** Command substring identifying a JVM process (e.g. "java"). */
    private String processCommandKeyword = "java";

    /** Argument substrings identifying the monitored legacy target processes. */
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

    /** JMX service URL of the separately running target JVM; blank disables remote diagnostics. */
    private String remoteJmxUrl = "";

    /** Hard limit for one remote JMX diagnostic round trip (detects hung targets). */
    private Duration remoteJmxTimeout = Duration.ofSeconds(3);

    public String getRemoteJmxUrl() {
        return remoteJmxUrl;
    }

    public void setRemoteJmxUrl(String remoteJmxUrl) {
        this.remoteJmxUrl = remoteJmxUrl;
    }

    public Duration getRemoteJmxTimeout() {
        return remoteJmxTimeout;
    }

    public void setRemoteJmxTimeout(Duration remoteJmxTimeout) {
        this.remoteJmxTimeout = remoteJmxTimeout;
    }

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
        this.nixRunTarget = nixRunTarget;
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
        this.probeHost = probeHost;
    }

    public int getProbePort() {
        return probePort;
    }

    public void setProbePort(int probePort) {
        this.probePort = probePort;
    }

    public Duration getProbeTimeout() {
        return probeTimeout;
    }

    public void setProbeTimeout(Duration probeTimeout) {
        this.probeTimeout = probeTimeout;
    }

    public Duration getHttpTimeout() {
        return httpTimeout;
    }

    public void setHttpTimeout(Duration httpTimeout) {
        this.httpTimeout = httpTimeout;
    }

    public int getFailureThreshold() {
        return failureThreshold;
    }

    public void setFailureThreshold(int failureThreshold) {
        this.failureThreshold = failureThreshold;
    }

    public int getIoFailureThreshold() {
        return ioFailureThreshold;
    }

    public void setIoFailureThreshold(int ioFailureThreshold) {
        this.ioFailureThreshold = ioFailureThreshold;
    }

    public double getHeapCriticalThreshold() {
        return heapCriticalThreshold;
    }

    public void setHeapCriticalThreshold(double heapCriticalThreshold) {
        this.heapCriticalThreshold = heapCriticalThreshold;
    }

    public DataSize getMinFreeDiskSpace() {
        return minFreeDiskSpace;
    }

    public void setMinFreeDiskSpace(DataSize minFreeDiskSpace) {
        this.minFreeDiskSpace = minFreeDiskSpace;
    }
}
