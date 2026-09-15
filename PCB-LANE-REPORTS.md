# PC-B hardening lanes report (post-LIVE-5 directive)

Repo: `daraujop77/JarvisAndroidApp`. PC-A known-good `56a74db` (`PA-11 = PASS`,
`WEB_PC_V1_OPERATIONAL = PASS`). PC-A `/api/v1`, approval bridge, pairing V1 and
upload V1 are *preparing*; PC-B consumes published schemas/fixtures only.

## Lane A — AND-W8 background/foreground reliability → **PASS (PREP)**
Decision: **no foreground service.** Streams live in app-scoped coroutines; while
the process lives (backgrounded), delivery continues; on process death the
LIVE-2/3 recovery path reconciles completed turns via `GET /api/app/chat/requests/{id}`
— no second inference, no duplicate text. A persistent notification would cost
battery + user-visible ongoing noise for zero V1 benefit.

Shipped:
- `NotifyPolicy` — pure, deterministic notification decisions (approval notify →
  cancel on resolve, per-request reply notifications suppressed in foreground,
  one-shot auth-expired/revoked transitions), **persisted** seen-IDs so a restart
  never re-notifies. 7 unit tests.
- `JarvisSessionRepository.setForeground` — backgrounded with no in-flight work
  parks reconnect; foreground resumes it; fail-closed phases never resume.
- Notification IDs use masked hash (`and 0x1FF`) — no negative ids.

`OWNER_DEVICE_TEST_REQUIRED` for the physical background-delivery pass.

## Lane B — daily-use network hardening → **PASS**
- `ReconnectPolicy` extracted: bounded backoff `[500,1s,2s,5s,10s]`, hard stop at
  `MAX_ATTEMPTS`; fail-closed phases (REVOKED/MISMATCH/AUTH_EXPIRED) never auto-retry.
- Bug found & fixed while testing: the backoff loop could overwrite the REVOKED
  latch with CONNECTING after sleeping; it now re-checks phase before dialing.
- Tests (`NetworkHardeningTest`, `ReconnectPolicyTest`): never ONLINE without a
  server-confirmed frame; bounded attempts (no storm, then provably stops);
  revoked device never auto-retries, even across background→foreground;
  background defers + foreground resumes.
- HTTP poller race fixed (delay-then-poll).

## Lane C — Room migration debt → **PASS**
- Reconstructed the true v1 schema from repo history: v1→v2 was the
  `pending_outbound.attachmentIds` column (AND-W6). Added explicit
  `MIGRATION_1_2` (recreate, no SQL default — matches Room's compiled v2 exactly).
- Registered `MIGRATION_1_2 + MIGRATION_2_3`; **no destructive fallback anywhere**.
- JVM chain test on real SQLite (`sqlite-jdbc`): v1→v2→v3 preserves
  conversations, messages, pending outbound, cursor (`42 → "42"`, `0 → ""`) and
  attempt counters; column-set assertions at every step.

## Lane D — transport switch → **DEFERRED_WITH_EVIDENCE** (runtime handoff) + security fix
Finding: the DI graph binds session-scoped singletons (`session` and
`conversations` capture `container.transport` at first touch; the collector runs
inside `JarvisSessionRepository`). A runtime FAKE↔LIVE↔HTTP swap means tearing
down/rebuilding that whole graph — which is exactly what the existing
**cold relaunch** does, safely and for free. Hand-rolling it would risk the very
failure modes the directive bans: duplicate collectors, double event
consumption, in-flight requests orphaned across transports, token crossing a
transport boundary. The login→relaunch path (LIVE-2) already delivers
Fake→LIVE and (future) `/api/app`→`/api/v1` with one tap.

Security fix shipped under this lane's criteria ("no public endpoint downgrade"):
`HttpGatewayTransport.requireBase` now enforces the same fail-closed
`isAllowedLiveHost` policy as LIVE — since HTTP reuses the real PC-A bearer, a
typo'd/public base URL must never see it. Test asserts rejection before any
socket (linkState FAILED, reason "private-network").

Gate: **DEFERRED_WITH_EVIDENCE** (this document + relaunch tests + host guard).

## Lane E — release/security hardening → **PASS**
- `assembleRelease` green with R8 (`minifyReleaseWithR8`); kotlinx.serialization
  keep-rules added to `proguard-rules.pro`; `buildConfig = true`.
- Debug-only surfaces: Fake toggle, SIMULATION picker, diagnostics card and the
  "Continue with Fake Gateway" button hide behind `BuildConfig.DEBUG`;
  `resolveMode` refuses FAKE in release even if a stray pref asks for it.
- Static secret scan of source/resources: clean (only a public-key PEM header).
  No `Log.*`/`println` in main sources; passwords never persisted (test-asserted);
  `allowBackup=false`; receiver `exported=false`; cleartext restricted by
  `network_security_config` *and* by the runtime host guard.
- Release APK is unsigned by design — no signing key committed (documented).

## Lane G — API V1 consumer readiness → **PASS**
- Audit: `HttpGatewayTransport` already speaks the frozen spine (opaque cursor,
  replay-after-cursor, auth seam, fingerprint fail-closed, additive tolerance,
  dedupe via the shared reducer). It now also reuses the real PC-A bearer via the
  `AuthProvider` seam when a session exists (no fabricated tokens; still
  fail-closed with none).
- New **transport conformance suite** (`TransportConformanceTest`): HTTP `/api/v1`
  and LIVE `/api/app` SSE, given equivalent server responses, must land the same
  reducer outcomes (identical text/status), suppress duplicate event_ids, never
  fabricate completion on a mid-stream drop, and fail closed on protocol 2.0.
  Production cutover stays gated on PC-A `API_V1_ACTIVE = PASS`.

## Lane H — real approval client readiness → **PASS (PREPARED_NOT_CONNECTED)**
Seam already exists (`MobileRequest.ResolveApproval` → transport). LIVE fails
closed with an explicit "approvals not enabled (PA-5/6)" error until PC-A
publishes the approval bridge — never silently. Gap tests added:
- server EXPIRED beats a local Approve (server authoritative);
- resolve for an unknown approval id is a no-op;
- network drop during resolution rolls back in-flight and a reconnect retry
  sends **exactly one** command;
- LIVE seam refusal asserted for both approval resolve and attachment upload.
Remaining on PC-A side: publish `approval` schemas/fixtures; PC-B then consumes
verbatim and flips one call site (documented in `PCB-LIVE-PROGRESS.md`).

## Lane F — AND-W9 Projects shell → **PASS_PREPARED_NOT_CONNECTED**
(see `projects` package; Fake repository only; no invented `/api/projects`.)

## Evidence
- `:app:testDebugUnitTest`: **100+ tests, 0 failures** (see git log per lane).
- `:app:assembleDebug` and `:app:assembleRelease` BUILD SUCCESSFUL.
- Commits: `c10593c` (laneC) · `b7590ff`+`3758466` (laneA) · `1487b1c` (laneB) ·
  `f74f81b` (laneE) · `fb0673e` (laneG) · `b892704` (laneH) · laneD/laneF follow.
