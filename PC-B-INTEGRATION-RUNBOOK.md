# PC-B Integration Runbook — Current Android Repo

Status: ACTIVE EXECUTION GUIDE FOR `daraujop77/JarvisAndroidApp`

PC-B owns this repository. PC-A continues the JARVIS Production Activation track in `daraujop77/jarvis`.

## 1. Current verified state of this Android repo

Do NOT restart the Android project from the old foundation plan.

This repo is already substantially ahead:

- AND-W0 debug build: DONE
- AND-W1 contract models + Fake Gateway + reducer: DONE against the repo's current mobile-shaped contract
- AND-W2 conversation MVP + Room + pending outbound + replay: DONE against Fake Gateway
- AND-W3 pairing: UI + Android Keystore device identity skeleton exists; real handshake is blocked on PC-A pairing contract
- AND-W4 live transport: WSS/HTTPS adapter skeleton exists, but is NOT yet compatible/proven against the current PC-A production Gateway
- AND-W5 approvals/tasks UI/state: largely present against fake scenarios
- AND-W6 media shell: present; binary upload remains blocked
- AND-W8 notification shell: present
- diagnostics and fake scenario matrix: present

Therefore the next work is NOT AIPC-001/002/003 from scratch.

The next work is a CONTRACT RECONCILIATION + LIVE-INTEGRATION PREP pass.

## 2. Authoritative PC-A baseline to consume

Current shared PC-A source of truth:

Repository:
`daraujop77/jarvis`

Main baseline at the time this runbook was created:
`2a6ba24ab398356409f0448d6b13d63df8bb382a`

Shared contract files:

- `contracts/web-v1/spine-0.json`
- `contracts/web-v1/request-envelope.schema.json`
- `contracts/web-v1/event-envelope.schema.json`
- `contracts/web-v1/approval-envelope.schema.json`
- `contracts/web-v1/fixtures/**`

Protocol:
`jarvis.web.v1 1.0`

PC-B consumes this shared contract. PC-B must not silently redefine it.

## 3. Important mismatch discovered — fix before live integration

The Android repo currently models a mobile-specific protocol that does not exactly match the frozen PC-A Web V1 contract.

### Android currently expects

Examples from current code:

- `EventEnvelope.cursor: Long`
- `eventId`
- `version`
- `timestampMs`
- nested `event { type, ... }`
- `MobileRequest.SendMessage`
- `MobileRequest.Replay(sinceCursor: Long)`
- WSS path `/mobile/events`
- health path `/health`
- no live auth header in WSS adapter yet.

### PC-A Web V1 defines

- `cursor`: opaque string replay position
- `event_id`
- `protocol_version`
- `contract_fingerprint`
- `timestamp_utc`
- top-level `type`
- top-level identity:
  - user_id
  - device_id
  - session_id
  - conversation_id
  - request_id
  - trace_id
  - task_id
  - run_id
  - action_id
- `payload`
- `optional`
- requests through `POST /api/v1/requests`
- operations:
  - submit
  - resume
  - cancel
  - action
- events through `/api/v1/events`
- health through `/api/v1/health`
- capabilities through `/api/v1/capabilities`

Do NOT connect the current WSS skeleton directly to PC-A and invent translations ad hoc.

First reconcile Android to the shared contract behind the existing `GatewayTransport` abstraction.

## 4. Immediate PC-B execution boundary

PC-B may advance now through:

1. `PCB-R0` — Shared-contract reconciliation
2. `PCB-R1` — Fake Gateway conversion to Web V1 semantics
3. `PCB-R2` — Room/cursor migration + process-death regression
4. `PCB-R3` — Live transport adapter preparation, but NO real PC-A connection

After PCB-R3:

STOP live integration unless PC-A reports:

`PA-1 = PASS`

and provides a stable authenticated/private front door.

If PA-1 is not green, report exactly:

`PC_B_READY_FOR_LIVE_GATEWAY_WAITING_FOR_PA1`

Do not bypass this gate.

## 5. PCB-R0 — Shared-contract reconciliation

Goal: make Android's protocol/domain adapter compatible with the frozen PC-A Web V1 contract while preserving existing UI/reducer behavior.

### Required work

Create Android serializable models/adapters for the actual PC-A:

- request envelope
- event envelope
- approval envelope
- health
- capabilities
- protocol fingerprint
- identity fields
- request states
- connection states

Prefer one of these safe strategies:

A. refactor Android contract models to mirror Web V1 exactly; or
B. add a thin WebV1 wire adapter that converts the frozen wire shape into stable internal reducer/domain events.

Do NOT create a new third protocol.

### Cursor

Treat the shared Gateway cursor as OPAQUE.

Do not parse it as a number.

Change Android persistence/API assumptions from numeric cursor to string/opaque replay token.

### Events

Support the frozen event types including:

- connection.ready
- connection.degraded
- state.changed
- message.accepted
- message.delta
- message.completed
- message.failed
- router.decision
- task.started
- task.progress
- task.completed
- task.failed
- tool.started
- tool.completed
- action.proposed
- action.started
- action.completed
- approval.required
- approval.resolved
- error

Unknown event behavior:

- unknown + optional=true → ignore safely + diagnostics
- incompatible protocol/fingerprint → fail visibly / UPDATE_REQUIRED

### PASS gate

PCB-R0 passes when Android tests can consume the actual checked-in PC-A Web V1 fixtures without rewriting those fixtures.

## 6. PCB-R1 — Convert Fake Gateway to the frozen contract

Goal: keep all the excellent existing Fake Gateway/reducer tests, but drive them through the same wire contract PC-A uses.

Preserve scenarios:

- accepted → delta → completed
- duplicate event
- out-of-order event
- replay after cursor
- cancel idempotency
- retryable/final failure
- approval
- tasks
- degraded/offline
- protocol mismatch

But serialize/deserialise using Web V1 envelopes.

Do not let Fake Gateway remain a mobile-specific alternate backend.

### PASS gate

- duplicate event_id does not duplicate UI text
- replay from opaque cursor does not duplicate text
- cancel remains idempotent
- protocol mismatch is visible
- additive optional event is safe
- existing fake UX still works

## 7. PCB-R2 — Room migration and durable state

The current Room DB is version 2 and still has destructive fallback.

Because cursor semantics change from `Long` to opaque `String`, make this an explicit schema migration instead of relying on destructive fallback.

Required:

- migrate stored cursor to nullable/string opaque token
- preserve conversations/messages/pending outbound
- export the new schema
- add Room migration test
- app-process death while streaming
- restart + replay
- pending outbound reconciliation
- no duplicate text
- conversation A never appears in B

Do not store:

- passwords
- cloud provider credentials
- pairing private keys
- raw long-lived secrets

### PASS gate

Migration and process-death tests green without destructive data loss.

## 8. PCB-R3 — Prepare the real PC-A transport

Goal: prepare the transport adapter so that once PA-1 is green, only endpoint/auth wiring and live validation remain.

### Do not assume WebSocket is required

The shared contract freezes semantics, not necessarily the final Android transport.

Current PC-A Web V1 baseline exposes HTTP request/replay semantics.

Prepare the adapter to support:

- GET `/api/v1/health`
- GET `/api/v1/capabilities`
- POST `/api/v1/requests`
- GET/replay `/api/v1/events?after=<opaque_cursor>`
- request status/resume/cancel according to shared contract

Keep the existing WSS implementation behind the `GatewayTransport` seam, but do not make `/mobile/events` a production requirement.

If PC-A later publishes WSS/SSE, add it as another transport implementation without changing domain/reducer semantics.

### Authentication seam

Do not invent credentials.

Prepare an injectable authenticated-request/header provider, but real token/device binding waits for PC-A.

The current WSS adapter must continue to fail closed if credentials are absent.

### Private link seam

Keep transport behind `GatewayTransport` / PrivateLink abstraction.

Do not hard-code Tailscale behavior into UI/domain.

### PASS gate

- HTTP/replay transport can be unit-tested against a local fake server/fixture
- endpoint paths match Web V1
- opaque cursor round-trips unchanged
- auth provider seam exists
- no live PC-A request is required to pass PCB-R3

## 9. Mandatory stop after PCB-R3

Check PC-A status.

If PC-A has NOT reported:

`PA-1 = PASS`

STOP live integration.

Report:

`PC_B_READY_FOR_LIVE_GATEWAY_WAITING_FOR_PA1`

Allowed while waiting:

- Android-only unit tests
- UI polish
- accessibility
- migration tests
- diagnostics
- Fake Gateway negative cases
- documentation
- non-network performance cleanup

Not allowed:

- public temporary Gateway
- direct Ollama
- direct Hermes
- direct PowerShell
- direct Action Gateway
- hard-coded PC-A tokens
- weakening authentication
- inventing a mobile-only backend

## 10. What happens when PA-1 is green

Then begin the first live integration packet:

`PCB-LIVE-1`

Purpose:

Android physical phone
→ approved private network
→ PC-A authenticated front door
→ JARVIS Gateway

Start with:

- health
- capabilities
- authentication/session
- status
- logout/revoke behavior

A legacy `/api/app/chat` call may be used only as a bounded smoke test if PC-A still exposes it.

The final conversation path must use shared Gateway V1 semantics.

## 11. Later live gates

As PC-A advances:

### PC-A PA-2 / PA-3

PC-B may implement/test:

- real request
- real local/Hermes answer
- accepted/streaming/completed
- cancel
- reconnect/replay

### PC-A PA-2C / PA-3R

PC-B may implement/test:

- OWNER route/model selector
- Auto/Hermes
- Fast/Normal/Mid/Deep
- validated cloud routes returned by server
- per-conversation route selection

Do not hard-code the cloud model catalog in the APK.

### PC-A PA-7M

Run MU-1:

- Web conversation A
- Android conversation B
- same OWNER
- simultaneous
- zero context crossover
- independent cancel/reconnect
- route selection isolated per conversation

### PC-A PA-5 / PA-6

Activate real Android approvals/actions.

Android only resolves approval.

Windows side effect remains:

Gateway → Action Gateway → Windows executor.

## 12. Pairing

Do not block the current reconciliation work on the final pairing handshake.

Current Keystore device-identity skeleton is valuable and should be preserved.

Real PROD pairing waits for PC-A to freeze:

- enrollment challenge
- device identity proof
- credential/session exchange
- revoke
- expiry
- lost-device flow

Do not invent the server handshake on PC-B.

## 13. Ownership

PC-B owns:

- this repo
- Android client code
- Android tests
- Android persistence
- Android transport adapters
- Android UI

PC-B does NOT own:

- PC-A Gateway authority
- Action Gateway policy
- Hermes authority
- cloud/privacy policy
- user-role authority
- model routing authority
- Windows executors
- shared Web V1 contract definition

If PC-B finds a shared-contract issue, write a finding and hand it to PC-A. Do not silently change PC-A semantics.

## 14. Start-of-cycle checklist

At each cycle:

1. inspect working tree
2. preserve uncommitted work
3. `git fetch`
4. record Android HEAD
5. read this runbook
6. obtain/read the current shared Web V1 files from `daraujop77/jarvis`
7. compare shared protocol version/fingerprint assumptions
8. select earliest unblocked PCB-R packet
9. run tests
10. commit evidence
11. continue automatically until mandatory stop gate

## 15. PC-B report format

After each packet:

```
TASK_ID:
ANDROID_REPO_SHA:
PC_A_BASELINE_SHA:
SHARED_PROTOCOL:
CHANGED_PATHS:
TESTS:
RESULT:
EVIDENCE:
KNOWN_LIMITATIONS:
ROLLBACK:
NEXT_TASK:
PC_A_DEPENDENCY:
```

When waiting:

`PC_B_READY_FOR_LIVE_GATEWAY_WAITING_FOR_PA1`

## 16. Immediate instruction to Codex on PC-B

Read this file first.

Do NOT redo AND-W0/W1/W2.

Start with `PCB-R0`.

Continue through `PCB-R1`, `PCB-R2`, and `PCB-R3` as long as tests are green and no shared-contract ambiguity requires PC-A.

Then obey the PA-1 stop gate.

