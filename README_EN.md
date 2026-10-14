📄 README_EN.md (Global Edition)


# ✝️ J.E.S.U.S (Java Ejectable Survival Unlimited Sustainable System)

> A Java 17 / Spring Boot watchdog prototype for legacy eGovFrame and enterprise systems.

---

## 🛡️ Overview
**J.E.S.U.S** is an early-stage Java 17/Spring Boot watchdog prototype for legacy enterprise services. It combines HTTP and TCP probes, optional JDBC checks, watchdog-JVM diagnostics, and disk/I/O checks. After configured failure conditions, it can forcibly terminate matching same-user Java processes and ask Nix to start the configured flake application.

This is not a production-certified recovery system. Recovery is destructive, depends on process matching and Nix being available, and should only be evaluated in a controlled environment. The watchdog's JVM and local filesystem checks describe the process and working directory where the watchdog itself runs; they do not inspect a remote target's JVM or disk.

---

## ⚡ Key Features

* **Layered probes**: HTTP(S) health endpoint, raw TCP connectivity, optional JDBC connection validation, watchdog-JVM heap/deadlock diagnostics, and local disk/write checks.
* **Threshold-based recovery**: Most probes require repeated failures before requesting ejection; a detected JVM deadlock and critically low disk space trigger immediately.
* **Actuator health**: `/actuator/health` reports the watchdog's aggregate monitor vitals. The HTTP endpoint it probes is the monitored target's endpoint.
* **Optional manual control**: `POST /api/jesus/eject-and-resurrect` is authorized by the `X-Watchdog-Token` header; requests are rejected unless a non-empty token is configured.

---

## 📂 Directory Structure

```text
Jesus_system/
├── .github/
│   └── workflows/
│       └── ci.yml                     # GitHub Actions CI/CD Pipeline
├── flake.nix                          # Multi-platform Nix shell & build architecture
├── pom.xml                            # Maven build config (Spring Boot 4.1.1)
└── src/
    └── main/
        ├── java/com/jesus/system/
        │   ├── JesusApplication.java              # Bootstrap Main
        │   ├── JesusWatchdogAutoConfiguration.java # Spring Boot Starter Auto-Configuration
        │   ├── config/
        │   │   └── WatchdogProperties.java        # Externalized jesus.watchdog.* settings
        │   ├── core/
        │   │   ├── ProcessLifecycleManager.java   # Single Eject & Nix Resurrection Core
        │   │   └── VitalsRegistry.java            # Shared Monitor Vitals Registry
        │   ├── monitor/
        │   │   ├── WatchdogAgent.java             # 0.5s HTTP probe and threshold check
        │   │   ├── AbyssProbeMonitor.java         # Raw TCP Socket Low-Level Probe
        │   │   ├── EgovWatchdogMonitor.java       # eGovFrame DB & WAS Dual Verification
        │   │   ├── JvmDiagnosticsMonitor.java     # Watchdog JVM heap and deadlock checks
        │   │   ├── DiskIoGuardMonitor.java        # Watchdog working-directory disk/I/O checks
        │   │   └── HttpVitalsProbe.java           # Shared HTTP Vitals Probe
        │   ├── api/
        │   │   └── WatchdogCommandController.java # Emergency Manual Control REST API
        │   └── health/
        │       └── WatchdogHealthIndicator.java   # Health Indicator Backed by Real Vitals
        └── resources/
            ├── application.yml                    # Server port (9090) & watchdog thresholds
            └── META-INF/spring/                   # Auto-configuration registration (starter mode)



## 🚀 Quick Start & Build


1. Enter Nix Environment (Recommended)


nix develop



2. Local Maven Build & Packaging


mvn clean verify



3. Run Application


mvn spring-boot:run



## 📦 Adopting into an Existing Spring App (Starter Mode)


Any Spring Boot 4 application using Java 17 or later (e.g. a compatible modernized eGovFrame service) can embed the watchdog with a single dependency:


<dependency>
    <groupId>com.jesus.system</groupId>
    <artifactId>jesus-watchdog-agent</artifactId>
    <version>1.0.0</version>
</dependency>


Adding the dependency makes the auto-configuration available, but does **not** activate it by default. To opt in, set `jesus.watchdog.enabled=true`; leave it absent or set it to `false` to keep the watchdog inactive. The standalone application explicitly opts in through its `application.yml`.

Configure `JESUS_WATCHDOG_MANUAL_CONTROL_TOKEN` to enable the manual endpoint. The default standalone server binds to `127.0.0.1:9090`; change the bind address only if remote access is intentional and protected by your network controls. The token is an additional authorization check, not a replacement for network-level access control.


⚠️ javax-era eGovFrame (Spring Boot 2.x) hosts cannot embed it directly due to the Jakarta namespace split — use the standalone mode for those systems.

## ⚙️ Configuration Reference

All watchdog settings use the `jesus.watchdog` prefix. The table lists the defaults declared by the application; an embedded host must supply any target-specific values it needs.

| Property | Default | Purpose |
| --- | --- | --- |
| `enabled` | `false` for embedded auto-configuration; `true` in standalone `application.yml` | Explicitly opts into watchdog registration and scheduled monitoring. |
| `process-command-keyword` | `java` | Required Java executable name when matching target processes. |
| `target-process-keywords` | `[egov, tomcat]` | A matching process must contain at least one keyword in its arguments. |
| `nix-run-target` | `.#jesus-server-17` | Nix flake target started after ejection. |
| `manual-control-token` | empty | Shared secret required by the manual control endpoint. Configure via `JESUS_WATCHDOG_MANUAL_CONTROL_TOKEN`. |
| `health-endpoint` | `http://localhost:8080/actuator/health` | HTTP(S) endpoint probed for target health. |
| `health-endpoint-allowed-origins` | `[http://localhost:8080]` | Explicit origin allowlist for the HTTP probe; include each intended target origin. |
| `probe-host` / `probe-port` | `127.0.0.1` / `8080` | Host and port used by the raw TCP probe. |
| `probe-timeout` / `http-timeout` | `300ms` / `1500ms` | TCP connect timeout and HTTP request timeout. |
| `failure-threshold` / `io-failure-threshold` | `3` / `2` | Consecutive failures required for health and I/O ejection. |
| `heap-critical-threshold` | `0.92` | Watchdog JVM heap usage ratio considered critical. |
| `remote-jmx-url` / `remote-jmx-timeout` | empty (disabled) / `3s` | JMX URL (loopback host only) of a separately running target JVM, and the hard limit for one diagnostic round trip. A refused or timed-out connection counts as a failure. |
| `heartbeat-file` / `heartbeat-max-age` | empty (disabled) / `15s` | File the target touches periodically; a missing or older file counts as a hang. |
| `restart-command` | empty | Optional program and arguments used to relaunch the target instead of the Nix respawn. |
| `min-free-disk-space` | `500MB` | Minimum available space in the watchdog's current working directory. |

Set these as Spring properties, environment variables, or YAML configuration. The HTTP allowlist compares scheme, host, and port; setting a remote `health-endpoint` without adding its origin to `health-endpoint-allowed-origins` makes the probe fail without sending a request.

## 🔍 Monitor Behavior

| Monitor | Interval | Recovery condition |
| --- | --- | --- |
| HTTP actuator probe | 500 ms | Eject after `failure-threshold` consecutive unsuccessful checks. |
| eGov composite | 2 s | Eject after `failure-threshold` consecutive failures of HTTP or configured `DataSource`; the JDBC check is skipped when no `DataSource` exists. |
| Raw TCP probe | 1 s | Eject after `failure-threshold` consecutive connection failures. |
| JVM diagnostics | 5 s | Heap pressure ejects after `failure-threshold` checks; deadlocked threads trigger immediately. |
| Remote JVM diagnostics (JMX) | 5 s | Only when `remote-jmx-url` is set; unreachable/hung target, heap pressure or deadlocks eject after `failure-threshold` checks. |
| Heartbeat file | 2 s | Only when `heartbeat-file` is set; stale or missing file ejects after `failure-threshold` checks. |
| Disk and I/O guard | 10 s | Low disk space triggers once per low-space incident; failed write probes eject after `io-failure-threshold` failures. |

Threshold counters are maintained independently by each monitor. A successful probe resets its counter, except heap pressure, which recovers one failure at a time when heap use falls below the configured threshold.

Process termination excludes the watchdog's own PID and only matches processes owned by the same OS user. The executable must match `process-command-keyword`, and at least one configured target keyword must appear in the process arguments. Matching processes are forcibly terminated; the configured Nix target is then started if no resurrection is already in progress.

## 🛡️ Failure Domains

| Failure | Responder |
| --- | --- |
| Target app JVM failure | This watchdog, running as a separate JVM (set `remote-jmx-url` to inspect the target's internals). |
| Watchdog JVM exit | OS service manager restart policy, e.g. launchd (`deploy/com.jesus.watchdog.plist`, `KeepAlive`) or systemd (`deploy/jesus-watchdog.service`, `Restart=always`). |
| OS or power failure | A watchdog/replica on another machine (not provided by this project). |

A live-but-hung process is detected through the JMX timeout, the heartbeat file and the HTTP/TCP probe timeouts.

## 🔐 Safety and Access

* Watchdog activation enables scheduled checks that can terminate processes. Test process matching and recovery configuration in an isolated environment before enabling it against real services.
* The manual API accepts only `POST /api/jesus/eject-and-resurrect` with an `X-Watchdog-Token` value equal to the configured token. It returns `401 Unauthorized` if the token is unset or incorrect.
* The standalone server binds to loopback and exposes only the Actuator health endpoint over HTTP by default. Avoid exposing the manual API to untrusted networks.
* `/actuator/health` describes this watchdog's reported monitor vitals; it is not a direct health response from the monitored target.
* The project is early-stage, uncertified, and not ready for operational deployment.


🧪 Test & Formal Verification (Nix)


1. Run the full test suite inside the reproducible Nix environment


nix develop -c mvn clean verify



2. Build the deployment artifact as a pure Nix derivation (offline, hash-pinned dependencies)


nix build .#jesus-server-17



ℹ️ The first nix build fails with an mvnHash mismatch error — paste the printed hash into flake.nix (mvnHash = "...") and re-run. From then on the build is fully reproducible.



## 📊 CI/CD Status


The GitHub Actions workflow runs `mvn clean verify` with Java 17 and separately runs OpenJML ESC on `FailureCounter.java`. Formal verification applies to that counter's contracts only, not to Spring or the complete application.


---
