# Operations and Troubleshooting

This document is a safety-oriented checklist for evaluating the watchdog.
The project is early-stage and is not certified or ready for production
deployment. Run recovery exercises only against disposable test processes in
an isolated environment.

## Before enabling monitoring

1. Read the [architecture guide](ARCHITECTURE.md) and the complete
   [configuration reference](CONFIGURATION.md).
2. Start with `jesus.watchdog.enabled=false`. The embedded starter is disabled
   unless the host explicitly opts in; the standalone sample opts in, so
   review its configuration before running it.
3. Identify exactly which same-user Java processes can be matched. The
   executable must match `process-command-keyword`, and at least one
   `target-process-keywords` entry must appear in the process arguments.
   Confirm that the watchdog's own process cannot be selected.
4. Confirm that `nix run <nix-run-target>` starts only the intended disposable
   test target. The watchdog starts this command after ejection but does not
   check whether the replacement is ready.
5. Set the health endpoint, TCP address, timeout, and HTTP origin allowlist
   for the test target. An endpoint outside the allowlist will fail without a
   request being sent.
6. Decide whether the checks that inspect the watchdog JVM and its working
   directory are appropriate for the deployment. They do not measure a remote
   target's heap or disk.
7. Keep the server bound to loopback unless remote access is explicitly
   required and separately protected. Keep the manual token out of source
   control and logs.
8. Test expected failures, recovery, repeated triggers, and recovery from
   low-disk conditions using disposable processes and data.

Do not enable the watchdog if process selection or the Nix target is
ambiguous.

## Build and start

Requirements for the Java watchdog are JDK 17 and Maven. From the repository
root:

```sh
mvn clean verify
mvn spring-boot:run
```

For the pinned Nix development environment:

```sh
nix develop -c mvn clean verify
nix build .#jesus-server-17
```

The standalone server binds to `127.0.0.1:9090` by default. The monitored
service is expected at `127.0.0.1:8080` by default; these are separate
processes and ports. The app exposes Actuator health by default and hides
health details. Review `src/main/resources/application.yml` before startup,
especially `jesus.watchdog.enabled`, the process keywords, and the Nix target.

The Ada and Rust interpreters are experimental companion projects, not
requirements for the watchdog's monitoring and recovery logic. Their Java
bridge integration tests use `JESUS_ADA_VM` and `JESUS_RUST_VM`; see the
[Ada JVM guide](../ada-jvm/README.md) for build details.

## Verify status

Check the watchdog's aggregate health endpoint locally:

```sh
curl --fail-with-body http://127.0.0.1:9090/actuator/health
```

The result is the watchdog's reported status, not the target service's direct
health result. The standalone configuration hides component details. A
successful response does not certify that process matching or Nix recovery is
safe.

Review application logs for monitor status, the reason for a recovery
request, candidate process termination, and whether the Nix process launch
was triggered. Avoid logging configured control tokens or other secrets.

## Manual recovery endpoint

The endpoint is destructive. Only configure a token and call it in an
isolated test environment:

Provide the test token to the server and the client through a secret manager
or another mechanism that does not put its value in shell history. The
following request assumes the client environment already contains the
`JESUS_WATCHDOG_MANUAL_CONTROL_TOKEN` variable:

```sh
curl --fail-with-body \
  -X POST http://127.0.0.1:9090/api/jesus/eject-and-resurrect \
  -H "X-Watchdog-Token: ${JESUS_WATCHDOG_MANUAL_CONTROL_TOKEN}"
```

The server returns `401 Unauthorized` when the configured token is blank or
does not match. The token is compared in constant time, but it is a shared
secret and does not replace TLS, network restrictions, or access controls.
Do not put real tokens in shell history, tickets, documentation, or
screenshots. Do not expose this endpoint to an untrusted network.

An accepted recovery returns `202 Accepted` with status `STARTED`; this means
the configured Nix command started, not that the service is healthy. A
concurrent recovery returns `409 Conflict` with status `IN_PROGRESS`, and a
failure to start Nix returns `500 Internal Server Error` with status `FAILURE`.

## Troubleshooting

| Symptom | Checks |
| --- | --- |
| No monitor activity in an embedded host | Confirm `jesus.watchdog.enabled=true`, the auto-configuration is on the classpath, and the host has not excluded it. |
| HTTP checks fail immediately | Check endpoint syntax, network reachability, response status, timeout, and exact scheme/host/port allowlist match. An allowlist mismatch does not send a request. |
| TCP check fails while HTTP works | Verify the raw TCP probe host and port; TCP acceptance and an HTTP health response are different checks. |
| eGov monitor reports database failure | Confirm the host exposes a working `DataSource`. If there is no `DataSource`, the JDBC part is skipped and the HTTP check remains active. |
| Watchdog health reports `DOWN` | Review the latest monitor logs and status. Any monitor's retained `DOWN` vital makes aggregate status `DOWN` until that monitor reports `UP`. |
| Manual API returns 401 | Confirm the token is non-empty and that the request header matches the configured value exactly. Keep the token secret while checking configuration. |
| No process is terminated | Verify the OS user, executable basename, and process arguments against the matcher rules. The watchdog deliberately excludes its own PID and other users' processes. |
| Nix restart does not happen | Confirm `nix` is installed and usable in the watchdog's environment and that the configured flake target is valid. The command's launch is not proof of service readiness. |
| A host's monitor checks delay one another | Inspect its `TaskScheduler` pool size and blocking workload. If the host provides the scheduler, ensure it has enough dedicated workers. |
| Low-space recovery repeats unexpectedly | The disk incident resets after usable space returns to or above the threshold; a later low-space event can request recovery again. |

## Stop and rollback

For an embedded application, disable the watchdog by setting
`jesus.watchdog.enabled=false` and restart the host. For standalone mode, stop
the watchdog process through the normal process supervisor. If monitoring is
triggering unintended actions, disable it first, then review logs and the
process/Nix configuration before attempting another test.

The watchdog does not implement a readiness gate, rollback strategy, or
operator approval workflow for automatic recovery. Plan those operational
controls outside this prototype before any real deployment is considered.

## Further reading

- [Watchdog architecture](ARCHITECTURE.md)
- [Configuration reference](CONFIGURATION.md)
- [Security policy](../SECURITY.md)
- [Requirements traceability](TRACEABILITY.md)
- [Accessibility statement](../ACCESSIBILITY.md)
