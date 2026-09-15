# PC-B LIVE progress register (Android)

Gateway: `jarvis.web.v1 1.0` frozen; live surface is PC-A's authenticated app
API (`/api/app/*`) over the private Tailscale front door, because PC-A does not
serve `/api/v1/*` yet. All live code stays behind the `GatewayTransport` seam —
UI/reducer semantics unchanged, no third protocol, no mobile-only backend, no
direct Hermes/Ollama/PowerShell/Action-Gateway.

Owner attested `PA-1 = PASS`. **Current PC-A state (supersedes older notes):**
`PA-11 = PASS`, `WEB_PC_V1_OPERATIONAL = PASS`, known-good baseline
`56a74db21cc6bb31eec26d9ed8b876a86e9e85f0`. PC-A is *preparing* (may be
PREPARED_NOT_ACTIVATED): real `/api/v1` surface, approval bridge, pairing V1,
attachment/upload V1 — PC-B consumes published schemas/fixtures only and marks
anything unpublished `WAITING_FOR_PC_A_CONTRACT`. Front door (app surface):
`http://desktop-l59hjk4/`.

## Per-packet record

| Packet | Implemented | Tests | Result |
|---|---|---|---|
| R0–R3 | Web V1 adapter, Fake→V1, Room opaque-cursor migration, HTTP `/api/v1` adapter | 74/0 | committed `f633703`+ |
| LIVE-1 | `JarvisAppSession` (login/session/logout, token-only storage, private-host fail-closed), `LiveAppGatewayTransport` (SSE→EventEnvelope, scoped cancel), PairingScreen connect form | +Live suite, 71/0 | `5586389` |
| LIVE-1 on-device | cleartext over Tailscale (network security config), pairing form above IME | — | `cf4f337` — **verified by owner: login LIVE worked on S25 Ultra** |
| LIVE-2 | bounded multi-turn context, completed-turn recovery (no 2nd inference), live login activates LIVE transport + cold relaunch | 76/0 | `70824c2` |
| LIVE-3 | SSE 404/405 → JSON `/api/app/chat` fallback; mid-stream drop → GET `/requests/{id}` recovery | 76/0 | `fa32a3d` |
| LIVE-4 | server-driven owner profile selector (chips from `/api/app/status` `chat.models[]`, gated by `owner_model_selection`); profile flows into turn payload | 78/0 | `6c3f740` |
| LIVE-5 | **conversation-local** profile selection (PA-7M isolation); concurrent A/B isolation tests (profile + context + conversation_id never cross) | 79/0 | `f3f6e21` |
| Lane C (hardening) | Explicit `Migration(1,2)` (pending_outbound.attachmentIds, reconstructed from pre-attachment schema) + registered 1→2 and 2→3; JVM **chain** test v1→v2→v3 preserving conversations/messages/pending/cursor/attempts; README debt note retired | 80/0 | laneC commit |
| Lane A (AND-W8) | No foreground service (documented decision). `NotifyPolicy`: pure background/foreground notification decisions with **persisted** seen-IDs (no re-notify after process death), per-request completion ids, one-shot auth-expired/revoked transitions, approval notify→cancel exactly-once. `JarvisSessionRepository.setForeground`: background+idle defers reconnect, foreground resumes; caps stay bounded (no retry storm). Fixed HTTP poller first-poll race (delay-then-poll). | 86/0 | laneA commit |

`TESTS: 86 unit tests, 0 failures` · `:app:assembleDebug BUILD SUCCESSFUL` at each commit.
Gates: `ROOM_MIGRATIONS_COMPLETE = PASS` · `AND_W8_PREP = PASS` (device confirmation: `OWNER_DEVICE_TEST_REQUIRED`).

## Latency / responsiveness measures in place
- Streaming: SSE deltas applied incrementally, out-of-order buffered by reducer.
- Immediate activity: typing dots before first delta, blinking caret while
  streaming, optimistic user bubble persisted before the wire.
- Off main thread: all HTTP/SSE on `Dispatchers.IO`; JSON/history read inside the
  transport dispatch coroutine, never the UI thread.
- Controlled retry/backoff: single reconnect job, reset on CONNECTED.
- Cancel: idempotent (reducer guarantees one wire command); mirrors auth.js
  (mark → scoped POST cancel → abort local call → settle).
- Timeouts: 10s connect, no read timeout for SSE (inference-bound), 15s write.
- Fallbacks: SSE-absent → JSON turn; mid-stream drop → cached-turn recovery.

## BLOCKED_BY_PC_A

### 1. Real PC-control approvals
- **Capacidad:** approval resolve over the live Gateway.
- **Necesario para:** resolving PC-action approvals from Android against real PC-A.
- **Contrato esperado:** `/api/v1/requests` `operation:action` with approval
  envelope (frozen in `contracts/web-v1`), gated by PC-A **PA-5 / PA-6**.
- **PC-B ya completó:** full Android approval UX — owner-only list/detail,
  approve/deny, expiry, idempotent resolve, biometric gate for SENSITIVE/CRITICAL,
  notifications, Fake-Gateway scenarios.
- **Restante con PC-A:** point approval resolve at the real endpoint; keep the
  same reducer semantics.
- **Workaround:** `LiveAppGatewayTransport` refuses `ResolveApproval`/`UploadAttachment`
  with an explicit error; approvals exercised on the Fake transport.

### 2. Attachment binary upload
- **Capacidad:** upload intent → binary transfer → `attachment.ready`.
- **Necesario para:** sending photos to a live conversation.
- **Contrato esperado:** PC-A upload contract (§11 freeze, not yet published).
- **PC-B ya completó:** photo picker, EXIF/GPS-stripped private staging, opaque
  attachment ids, chips in bubbles, Fake confirm/reject.
- **Workaround:** upload intent refused on LIVE until the contract freezes.

### 3. Cryptographic pairing / device identity
- **Capacidad:** QR enrollment challenge, device proof, credential exchange,
  revoke, expiry.
- **Necesario para:** production pairing (replaces username/password login).
- **Contrato esperado:** PC-A pairing freeze (runbook §12).
- **PC-B ya completó:** Android Keystore non-exportable identity skeleton,
  device id fingerprint.
- **Workaround:** existing PC-A `/api/app/login` bearer session (owner-directed),
  token-only storage; no invented handshake.

### 4. Real `/api/v1` Gateway front door
- **Capacidad:** the frozen Web V1 HTTP surface (`/api/v1/health|capabilities|
  requests|events`) served by PC-A.
- **Necesario para:** retiring the app-session adapter; full cursor replay.
- **Contrato esperado:** already frozen (`spine-0.json`); PC-A not serving it yet.
- **PC-B ya completó:** HTTP `/api/v1` adapter + opaque cursor + fixtures consumed
  verbatim (PCB-R0/R3). Swap is a one-line `AppContainer` mode change.

### 5. On-device LIVE-2..5 end-to-end verification
- **Capacidad:** observing stream/cancel/recover/profile on the phone through the
  overlay (feeds PC-A **PA-8** private-remote evidence).
- **Necesario para:** marking LIVE-2..5 "device-verified" (not just unit-green).
- **Restante:** phone connected (USB drops frequently) + PC-A gateway up on the
  tailnet; run each surface once.
- **Workaround:** every path is covered by MockWebServer tests mirroring PC-A's
  exact response shapes.

## Not blocked / intentionally out of scope
- Direct provider/model routing, cloud catalog, hard-coded tokens: **not done on
  purpose** (runbook forbids; server-driven catalog only).
- Foreground-service sync (AND-W8), Tailscale-in-UI wiring: deferred, non-blocking
  for LIVE E2E while the app is foregrounded.

## Freeze-window independent work
PC-A reaudit of `c97fa93`: `CROSS_SYSTEM_GATE = BLOCKED` on four remaining
client gaps (capabilities shape, request `task_id`/`run_id`, required event
fields, typed HTTP error envelope). Those four are closed in the dedicated
`fix: close PC-B cross-system contract gaps` commit. Fingerprint unchanged.
No `/api/v1` production cutover, no live approvals/pairing/uploads.

## Next task
Wait for PC-A reaudit of the `fix: close final PC-B cross-system gaps`
commit (capabilities schema+fingerprint required; typed error envelope
complete). Owner device pack remains `DEVICE-HUMAN-CHECK.md` — rebuild the
sideload APK only after that SHA is the new Human Check baseline. Parked
Web V1 WIP stash was inspected and **not** applied (broken compile, would
regress AUTH / task-run / event-required).
