# Watchdog Configuration Reference

All watchdog settings use the `jesus.watchdog` prefix. The values below are
the defaults in `WatchdogProperties` and the standalone `application.yml`.
Spring Boot hosts can set them in YAML, properties files, or through Spring's
environment-variable binding. Configuration is not automatically copied from
the standalone application into an embedding host.

## Settings

| Setting | Default | Meaning and constraints |
| --- | --- | --- |
| `enabled` | `false` for embedded auto-configuration; `true` in the standalone app | Explicitly registers the watchdog and schedules its checks. Keep this disabled unless destructive process recovery is intentional. |
| `process-command-keyword` | `java` | Required executable basename for process matching. Matching is case-insensitive; an executable may also end in `.exe`. |
| `target-process-keywords` | `[egov, tomcat]` | A candidate process must contain at least one non-empty keyword in its arguments. Matching is case-insensitive. |
| `nix-run-target` | `.#jesus-server-17` | Argument passed to `nix run` after ejection. It must not be blank. |
| `manual-control-token` | empty | Shared token required by the manual endpoint. An empty value disables successful authorization. |
| `health-endpoint` | `http://localhost:8080/actuator/health` | HTTP(S) URL queried by the HTTP monitors. |
| `health-endpoint-allowed-origins` | `[http://localhost:8080]` | Explicit origins the HTTP probe may contact. Scheme, host, and effective port must match. Do not include a path, query, or fragment in an allowed origin. |
| `probe-host` | `127.0.0.1` | Host used by the raw TCP probe. Must not be blank. |
| `probe-port` | `8080` | TCP port used by the raw probe; must be in the range 1–65535. |
| `probe-timeout` | `300ms` | TCP connection timeout; must be positive, at least one millisecond, and fit the socket API's integer-millisecond limit. |
| `http-timeout` | `1500ms` | HTTP connection/request timeout; must be positive. |
| `failure-threshold` | `3` | Consecutive failures required by HTTP, composite eGov, TCP, and heap-pressure monitors; must be positive. |
| `io-failure-threshold` | `2` | Consecutive failed write probes required for I/O ejection; must be positive. |
| `recovery-cooldown` | `30s` | Minimum time between recoveries; must not be negative. |
| `recovery-window` | `10m` | Sliding window used to count recoveries; must be positive. |
| `max-recoveries-per-window` | `3` | Recoveries allowed per window before further ones are suppressed (restart-loop guard); must be positive. |
| `heap-critical-threshold` | `0.92` | Heap-use ratio considered critical, from greater than zero through 1.0 inclusive. |
| `min-free-disk-space` | `500MB` | Minimum usable space in the watchdog's current working directory. The value cannot be negative. |

Spring relaxed binding maps kebab-case properties to environment-variable
names such as `JESUS_WATCHDOG_PROBE_PORT` and
`JESUS_WATCHDOG_HEALTH_ENDPOINT`. For lists and complex deployment
configurations, prefer a YAML or properties file and verify the effective
configuration before enabling recovery. The standalone app reads its manual
control token from `JESUS_WATCHDOG_MANUAL_CONTROL_TOKEN`.

## Example

This example changes the target endpoints and explicitly authorizes the
remote HTTP origin. Replace the sample values for a controlled test
environment; do not copy them blindly into a live deployment.

```yaml
jesus:
  watchdog:
    enabled: false
    health-endpoint: https://service.example.invalid/actuator/health
    health-endpoint-allowed-origins:
      - https://service.example.invalid
    probe-host: service.example.invalid
    probe-port: 8443
    probe-timeout: 500ms
    http-timeout: 2s
    failure-threshold: 3
    io-failure-threshold: 2
    heap-critical-threshold: 0.92
    min-free-disk-space: 1GB
```

The example leaves `enabled` false intentionally. Change it only after
reviewing process selection, network access, Nix behavior, and all recovery
consequences.

## Origin allowlist behavior

The HTTP probe parses the endpoint and compares its origin with the configured
allowlist before sending a request. Only HTTP and HTTPS are accepted. The
comparison uses scheme, host, and port (including the standard default port
when omitted); URL paths are not part of the comparison. User information in
the endpoint is rejected. An unlisted or invalid endpoint is treated as an
unsuccessful check, and no HTTP request is sent.

For example, an endpoint at `https://service.example.invalid:8443/health`
requires an allowed origin of `https://service.example.invalid:8443`.
Allowlisting an origin grants the probe permission to contact that origin, so
keep the list as narrow as possible. This application-level allowlist is not a
replacement for network egress controls.

## Monitor schedule and trigger summary

| Monitor | Interval | Trigger |
| --- | --- | --- |
| Target HTTP health | 500 ms | Recovery request after `failure-threshold` consecutive unsuccessful checks. |
| eGov composite | 2 s | Recovery request after `failure-threshold` consecutive failures of HTTP or an available `DataSource`; without a `DataSource`, only HTTP is checked. |
| Raw TCP | 1 s | Recovery request after `failure-threshold` consecutive connection failures. |
| Watchdog JVM diagnostics | 5 s | Heap pressure after `failure-threshold` checks; detected deadlock immediately. |
| Watchdog working-directory disk and I/O | 10 s | Low usable space once per low-space incident; write failures after `io-failure-threshold` consecutive failures. |

Each monitor maintains its own failure history. Concurrent monitors can
independently request recovery; the lifecycle manager prevents overlapping
Nix launches while a launched process is still running, but does not provide a
target-readiness or deployment-rollback mechanism.

## Scheduler behavior

When no host `TaskScheduler` bean exists, auto-configuration creates a
five-thread scheduler. Its error handler logs unexpected scheduled-task
exceptions so later scheduled executions can proceed. An embedding host that
provides its own scheduler should reserve enough worker threads for all
watchdog checks and avoid sharing a constrained pool with blocking work.

## Related guides

- [Architecture and recovery lifecycle](ARCHITECTURE.md)
- [Operations and troubleshooting](OPERATIONS.md)
- [Application defaults](../src/main/resources/application.yml)
