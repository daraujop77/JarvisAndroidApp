# PC-B checkpoint — frozen base during PC-A Cross-System Gate verification

STATUS: **PC_B_LOCAL_CROSS_SYSTEM_GATE_PASS_WAITING_PC_A_RECORD**

Local reaudit (read-only harness, no runtime mutation) of
`cffcfe4764fa4ef7d6ec268ff8071c00c1e3ed4c`:
`CROSS_SYSTEM_GATE = PASS` on PC-B. PC-A has not recorded this yet.
Do not start PC-4 or the physical Human Check until PC-A does.

`decef003` reaudit: `CROSS_SYSTEM_GATE = BLOCKED` on capabilities-strict +
typed-error-complete. Follow-up code SHA is the `fix: close final PC-B
cross-system gaps` commit (see git log). Fingerprint unchanged.

Previous freeze SHA audited by PC-A: `c97fa93016c8ae79665618b1eba8a6adbe31239a`
(`CROSS_SYSTEM_GATE = BLOCKED` on four remaining gaps; auth-provider already PASS).

Verified-code HEAD at freeze: `c97fa93016c8ae79665618b1eba8a6adbe31239a`
(`main`, in sync with `origin/daraujop77/JarvisAndroidApp` before this
docs-only checkpoint commit; no code changes have followed it).

Contract freeze honored: `app/src/main/java/com/jarvis/android/contract/**`
and `app/src/test/resources/contracts/**` are byte-identical to the recorded
baseline (git diff empty). Web V1 schemas, the pinned fingerprint
`445e4013df96ad986232eaecec6c58c01bab472e2a55c5ec11c54536de552021`, envelope
shapes and transport wire formats are **unchanged**.

## Regression run (fresh, `--rerun-tasks`, no cached results)
`Wed 2026-09-15 ~04:26–04:31 UTC` on HEAD `c97fa93`, JDK 21 (Android Studio JBR),
Gradle 8.13 / AGP 8.11.1.

| # | Required category | Suites / evidence | Result |
|---|---|---|---|
| 1 | Unit tests (full) | 14 suites, **106 tests, 0 failures, 0 errors** | PASS |
| 2 | Transport / conformance | `TransportConformanceTest` (HTTP `/api/v1` ≡ LIVE `/api/app` domain outcomes; duplicate event_id; mid-stream; protocol 2.0 fail-closed), `WebV1FixtureTest` (official PC-A fixtures verbatim), `ContractCodecTest` | PASS |
| 3 | Room migrations | `CursorMigrationTest` (v1→v2→v3 chain on real SQLite; conversations/messages/pending/cursor/attempts preserved; no destructive fallback) | PASS |
| 4 | Reconnect / recovery | `NetworkHardeningTest` + `ReconnectPolicyTest` (bounded backoff, no storm, REVOKED latch never overwritten, background defer/foreground resume); `ProcessDeathReconciliationTest`; LIVE-2/3 recovery in `LiveAppGatewayTransportTest` (cached-turn recovery, JSON fallback, cancel idempotence) | PASS |
| 5 | Approval readiness | Reducer approval tests (server EXPIRED authoritative, unknown id no-op, idempotent resolve), Fake e2e (drop-during-resolve → retry exactly once), LIVE seam explicit refusal until PA-5/6 | PASS |
| 6 | Debug build | `:app:assembleDebug` → `app-debug.apk` (21,135,492 B) | PASS |
| 7 | Release build | `:app:assembleRelease` incl. `minifyReleaseWithR8` → `app-release-unsigned.apk` (2,799,197 B) | PASS |
| 8 | Security / fail-closed | `LiveHostPolicyTest` (public hosts rejected pre-request; loopback/RFC1918/CGNAT/`*.ts.net`/MagicDNS allowed), HTTP private-host guard (`HttpGatewayTransportTest.publicHostIsRejectedBeforeAnyRequest`), token-never-persisted-password test, auth-fail-closed session tests | PASS |

Fake-Gateway pipeline (`FakeGatewaySessionTest` 17) and `ProjectsRepositoryTest`
(Lane F shell, backend NOT_CONNECTED) also green.

## Changes made while PC-A verified
None to code. This checkpoint file is the only addition (docs). Working tree
was already clean and fully pushed; nothing to fix — full regression was green
on first fresh run, so no incremental commits were required.

## Contract incompatibilities detected
None blocking, re-confirming for the record:
1. PC-A live server still serves only `/api/app/*`; frozen `/api/v1` routes are
   implemented client-side but unverified against a live server until PC-A
   announces `API_V1_ACTIVE = PASS` (conformance suite already proves both
   adapters produce identical domain events for equivalent fixtures).
2. Approval resolve + attachment upload have explicit fail-closed refusals on
   the LIVE transport pending PC-A's published schemas (PA-5/6 + upload V1).
3. Pairing V1 unpublished: LIVE-1 continues using the existing
   `/api/app/login` bearer-session surface per owner direction; Keystore
   identity is provisioned but not part of any wire contract yet.
No shared-contract change is requested from PC-A.

## Addendum (after the fresh regression, still during the freeze)
Test-only hardening additions (no production/wire change, contract untouched):
- SSE stream tolerates interleaved junk (unknown event names, malformed JSON
  deltas, comment lines) and still settles the turn.
- HTTP poller failure flips the link to RECONNECTING so bounded backoff takes
  over (no silent spin).
Full suite re-run after all additions: **109 tests, 0 failures**,
`:app:testDebugUnitTest` + builds remain green. Also added a PA-7M-style merge
isolation test (two conversations streaming concurrently: each conversation's
Room+live view contains only its own request ids and text). These land as
`test:` commits; the verified wire/contract baseline recorded above is unchanged.

## Addendum 2 (still during freeze — no wire/contract change)
Independent daily-use / privacy hardening:
- `FLAG_SECURE` on `MainActivity` (recents + screenshots blanked).
- PROTOCOL_MISMATCH now fail-closes the MAIN shell (same pairing lock as
  REVOKED / AUTH_EXPIRED) with an UPDATE REQUIRED copy.
- Banner labels aligned to the product states: AUTH REQUIRED / UPDATE REQUIRED.
- Tests: AUTH_EXPIRED and PROTOCOL_MISMATCH never auto-retry across
  background→foreground.
Suite: **111 tests, 0 failures**. `:app:assembleDebug` PASS. Contract tree
untouched (`contract/**` and fixtures identical to freeze HEAD).

## Reaudit package (four remaining gaps, client-only)

Fingerprint unchanged: `445e4013df96ad986232eaecec6c58c01bab472e2a55c5ec11c54536de552021`.
No PC-A change. No schema relaxation. No third contract.

| Gap | Result |
|---|---|
| 1. Capabilities official shape (`protocol_version`, `server_version`, `capabilities`) | **PASS** |
| 2. `task_id` / `run_id` on every `/api/v1/requests` operation, stable across retry | **PASS** |
| 3. Required event fields: absent → malformed; present-null allowed only where schema is `string\|null` | **PASS** |
| 4. Typed Web V1 HTTP error envelope (no `HTTP <status>: <raw body>`) | **PASS** |

`SUPPORTED_TEST_JDK = 21` (Android Studio JBR `21.0.10`). Gradle 8.13 / AGP 8.11.1.
Java 25.0.2 is an environment issue on PC-A (`Kotlin IllegalArgumentException: 25.0.2`), not an Android contract issue.

## Outstanding (owner-side, does not affect the gate)
Physical device pass per `DEVICE-VERIFICATION-CHECKLIST.md`
(`OWNER_DEVICE_TEST_REQUIRED` on AND-W8/LIVE device evidence).
