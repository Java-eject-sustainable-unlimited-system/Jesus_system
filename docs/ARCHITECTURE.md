# Watchdog Architecture

This guide describes the Java watchdog as it exists in this repository. It is
an implementation overview, not a guarantee that recovery will succeed or a
claim that the software is suitable for production.

## Scope and process model

The watchdog can run as a standalone Spring Boot application or as an
opt-in Spring Boot starter. In standalone mode, the watchdog is its own Java
process and monitors a target service expected at the configured HTTP and TCP
addresses. In starter mode, the monitor components run inside the host
application itself. The process diagnostics and filesystem checks always
inspect the process and working directory in which the watchdog components are
running; they do not inspect a remote target's JVM or filesystem.

The Spring Boot 4 starter auto-configuration is conditional on
`jesus.watchdog.enabled=true`. Adding the artifact to a host's classpath does
not, by itself, activate scheduled monitoring. The standalone application's
`application.yml` explicitly enables it. Hosts from the older Spring Boot 2 /
`javax` era should use standalone mode because of the Jakarta namespace
boundary.

## Component map

| Component | Responsibility |
| --- | --- |
| `JesusWatchdogAutoConfiguration` | Registers the watchdog when enabled, binds settings, enables scheduling, and supplies a five-thread scheduler only when the host has not supplied one. |
| `WatchdogProperties` | Binds `jesus.watchdog.*` settings and validates selected safety-critical values during configuration. |
| `HttpVitalsProbe` | Sends HTTP GET requests only to HTTP(S) origins on the configured allowlist; considers 2xx and 3xx responses alive. |
| `WatchdogAgent` | Probes the target health URL every 500 ms and requests recovery after consecutive failures. |
| `EgovWatchdogMonitor` | Every 2 seconds combines the target HTTP check with an optional `DataSource` validation. The JDBC part is skipped when no data source exists. |
| `AbyssProbeMonitor` | Every second checks whether the configured target TCP port accepts a connection. |
| `JvmDiagnosticsMonitor` | Every 5 seconds inspects the watchdog JVM's heap usage and deadlocked threads. |
| `DiskIoGuardMonitor` | Every 10 seconds checks usable space for the watchdog's current directory and writes a temporary file there. |
| `VitalsRegistry` and `WatchdogHealthIndicator` | Retain each monitor's latest status and expose aggregate watchdog health through Actuator. |
| `ProcessLifecycleManager` | Selects matching same-user Java processes, forcibly terminates matches, and starts the configured Nix flake target. |
| `WatchdogCommandController` | Provides an optional authenticated manual ejection endpoint. |

## Monitoring and health reporting

Each monitor owns its own failure counter. A successful health, TCP, JDBC /
HTTP composite, or I/O check clears that monitor's consecutive failures. Heap
pressure recovers one failure at a time when usage falls below its threshold.
The disk-space incident path triggers once while space remains below the
configured minimum; it can trigger again after space recovers and later falls
below the minimum.

The default monitor intervals and configured failure thresholds are listed in
the [configuration reference](CONFIGURATION.md). An interval is not a
guaranteed end-to-end recovery time: checks can take up to their configured
timeouts, scheduler workers can be occupied, and process termination waits for
confirmation.

Every monitor writes its latest `UP` or `DOWN` vital to the shared registry.
The watchdog's `/actuator/health` status is `DOWN` if any retained vital is
`DOWN`, and `UP` otherwise. Before a monitor reports its first vital, the
registry may not yet reflect that component. In the standalone configuration,
Actuator health details are hidden (`show-details: never`). This endpoint is
the watchdog's aggregate status, not the target's health URL.

## Recovery lifecycle

1. A scheduled check or an authorized manual request decides that recovery is
   needed.
2. `ProcessLifecycleManager` enumerates local processes and excludes the
   watchdog's own PID.
3. A candidate must be owned by the same OS user, match `process-command-keyword`
   as an executable basename (with optional `.exe`) or exact configured path,
   and have a complete argument exactly matching a configured target value.
   Matching is case-insensitive.
4. The manager requests forcible termination for each match and waits up to
   five seconds for confirmation per process. It does not launch a replacement
   if any target fails to confirm termination.
5. One lifecycle guard covers ejection and Nix launch; concurrent triggers are
   rejected until the launched process exits. Launching the command is not a
   readiness check: the watchdog does not verify that the replacement service
   became healthy before reporting that launch was triggered.

The manual endpoint reports `202 Accepted` when the Nix launch is triggered,
`409 Conflict` when another lifecycle operation is in progress, and
`503 Service Unavailable` when termination or launch fails. These responses do
not establish that the replacement service is healthy.

These actions are destructive. A broad process keyword or incorrect target can
terminate an unintended same-user Java process. Validate process matching,
Nix availability, target selection, and recovery behavior in an isolated
environment before enabling the watchdog.

## JVM interpreter prototype

The separate `ada-jvm/` project contains limited Ada and Rust bytecode
interpreters. `AdaRustJvmBridge` invokes executable files as child processes,
compares Ada and Rust results for shared raw bytecode, and can ask Ada to
extract and execute a selected static method from a class file. This is not
JNI, is not part of watchdog recovery, and does not make either interpreter a
complete or Java-compatible JVM. See the [Ada JVM guide](../ada-jvm/README.md).

## Related guides

- [Configuration reference](CONFIGURATION.md)
- [Operations and troubleshooting](OPERATIONS.md)
- [Requirements traceability](TRACEABILITY.md)
- [Contributing and verification](../CONTRIBUTING.md)
