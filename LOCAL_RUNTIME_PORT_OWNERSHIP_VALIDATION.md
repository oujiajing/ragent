# Local Runtime Port Ownership Validation

Date: 2026-09-05

Result: `PASS`

## Final port table

| Service | Host ports | Actual owner | Result |
|---|---:|---|---|
| TEI / bge-m3 | 18083 | `tei-bge-m3` → container port 80 | PASS |
| RocketMQ NameServer | 9876 | `rmqnamesrv` | PASS |
| RocketMQ broker remoting | 10909, 10911, 10912 | `rmqbroker` | PASS |
| RocketMQ proxy | 18080, 18081, 18082 | `rmqbroker` | PASS |
| ragent backend | 9090 | ragent JVM | PASS |
| ragent frontend | 5173 | Vite | PASS |

RocketMQ does not publish 18083. No configured local service publishes the same host
port as TEI.

## Original conflict

The previous TEI container was configured for host port 18080, while RocketMQ's proxy
also published 18080. TEI then exited and ragent reported `unexpected end of stream`.
The fixed layout reserves 18083 for TEI and leaves RocketMQ on 18080-18082.

## Changes

- `bootstrap/src/main/resources/application.yaml` now permanently points the local
  TEI provider to `http://127.0.0.1:18083`.
- Added `scripts/local-runtime.ps1` for port ownership checks and TEI embedding smoke.
- Added `scripts/start-tei.ps1` using the existing bge-m3 model, GPU configuration,
  host port 18083, and Docker restart policy `unless-stopped`.
- `scripts/start-dev.ps1` now performs port preflight and starts/waits for TEI before
  starting ragent.
- `scripts/check-dev.ps1` now reports separate TEI container, endpoint, and bge-m3
  embedding states and fails when TEI is unavailable or ownership is unexpected.
- `scripts/stop-dev.ps1` stops the ragent backend/frontend and the TEI container only;
  it does not remove volumes, databases, or RocketMQ.
- Existing local embedding regression scripts were moved from 18080 to 18083.
- RocketMQ Compose files and the temporary smoke Compose now explicitly reserve only
  18080-18082 for RocketMQ.

## First startup validation

Command:

```powershell
.\scripts\start-dev.ps1 -StartRocketMq
.\scripts\check-dev.ps1
```

Results:

- TEI container: PASS
- TEI endpoint 18083: PASS
- bge-m3 fixed-text embedding smoke: PASS
- RocketMQ NameServer 9876: PASS
- ragent backend 9090: PASS
- ragent frontend 5173: PASS
- Port ownership preflight: PASS

The fixed smoke text was `施工现场临边应设置防护栏杆。`; the response was HTTP-success,
non-empty, finite, and suitable for the existing 1024-native/1536-application path.

## Stop/start restart validation

The following sequence was executed without manual port edits:

```powershell
.\scripts\stop-dev.ps1
.\scripts\start-dev.ps1 -StartRocketMq
.\scripts\check-dev.ps1
```

Second startup results:

- TEI restarted as `tei-bge-m3` on `18083` with `unless-stopped`: PASS
- RocketMQ remained on `9876`, `10909`, `10911`, `10912`, `18080-18082`: PASS
- ragent backend/frontend readiness: PASS
- `check-dev.ps1`: PASS

The final ragent JVM command line used only `--spring.profiles.active=local` and
`--server.port=9090`; it did not use a temporary TEI URL override. The permanent
18083 endpoint came from local application configuration.

## Remaining dynamic or temporary overrides

No runtime endpoint override was used in the final startup validation. Public-cloud
RocketMQ configuration remains separately environment-driven by design and is not
part of this local port plan.
