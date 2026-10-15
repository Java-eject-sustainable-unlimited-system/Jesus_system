# Watchdog HTTP API

The watchdog exposes two HTTP endpoints when it is active. The standalone
application binds to `127.0.0.1:9090` and exposes only the Actuator health
endpoint by default. The endpoints describe or control the watchdog; they are
not endpoints served by the monitored application.

## Health

```http
GET /actuator/health
```

This is Spring Boot Actuator's aggregate health for the watchdog monitors. It
reports `UP` until a monitor has reported a failure, then reports `DOWN` until
the monitors report recovery. It does not directly query the target when the
endpoint is requested. The default configuration hides health details in the
HTTP response (`management.endpoint.health.show-details: never`).

The watchdog's listening address and port are configured separately from the
target's health URL. The standalone defaults are `127.0.0.1:9090` for the
watchdog and `http://localhost:8080/actuator/health` for the monitored target.

## Manual ejection and resurrection

> **Warning:** This endpoint forcibly terminates matching Java processes owned
> by the watchdog's OS user, then attempts to start the configured Nix target.
> Test process-matching and recovery settings in an isolated environment
> before calling it.

```http
POST /api/jesus/eject-and-resurrect
X-Watchdog-Token: <configured-token>
```

The request has no body. Configure a non-empty
`jesus.watchdog.manual-control-token` (the standalone configuration reads it
from `JESUS_WATCHDOG_MANUAL_CONTROL_TOKEN`) to enable the endpoint. A missing,
incorrect, or unconfigured token returns `401 Unauthorized` and does not invoke
ejection. The token is an additional check, not a substitute for network-level
access controls. Keep the server bound to loopback unless remote access is
intentional and protected.

For a deliberate local test, provide the token through the environment rather
than placing it directly in shell history:

```sh
curl --fail-with-body -X POST \
  -H "X-Watchdog-Token: ${JESUS_WATCHDOG_MANUAL_CONTROL_TOKEN}" \
  http://127.0.0.1:9090/api/jesus/eject-and-resurrect
```

On an authorized request, the endpoint returns `200 OK` with a JSON object:

| Field | Meaning |
| --- | --- |
| `status` | `SUCCESS` means the authorized control operation ran; it does not guarantee that a target was terminated or restarted. |
| `action` | `EJECT_AND_RESURRECT_EXECUTED` |
| `processesTerminated` | Number of matching processes whose termination was confirmed. |
| `resurrectionTriggered` | Whether starting the configured Nix target was triggered. `false` can mean a resurrection was already in progress or the start failed. |
| `timestamp` | Time the report was created, as Unix epoch milliseconds. |

The target matching rules and Nix target are controlled by the
`jesus.watchdog.process-command-keyword`, `jesus.watchdog.target-process-keywords`,
and `jesus.watchdog.nix-run-target` settings described in the
[configuration reference](../README_EN.md#configuration-reference).
