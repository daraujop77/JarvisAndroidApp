# PC-B regression bound to `decef003` (PC-A reaudit SHA)

STATUS: **PC_B_READY_FOR_CROSS_SYSTEM_RESULT_AND_OWNER_DEVICE_CHECK**

**HEAD audited (do not treat later docs-only commits as the contract SHA):**  
`decef0039db8e8f8e7109ea21cf2a2191dfb8a4d`  
Message: `fix: close PC-B cross-system contract gaps`  
Repo: `daraujop77/JarvisAndroidApp` `main`

This report is **not** for `c97fa93` / `c46c5c2`. PC-A reaudit target is `decef003` only.

## Toolchain (fresh run)

- `SUPPORTED_TEST_JDK = 21`
- Android Studio JBR `21.0.10` (`C:\Program Files\Android\Android Studio\jbr`)
- Gradle 8.13 / AGP 8.11.1
- Command: `gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease --rerun-tasks`
- Java 25.0.2 is **not** used here (PC-A env issue, not this tree)

## Results (fresh `--rerun-tasks`, 2026-09-15 ~09:02–09:11 UTC)

| # | Category | Evidence | Result |
|---|---|---|---|
| 1 | Full unit suite | 15 suites, **129 tests, 0 failures, 0 errors** | **PASS** |
| 2 | Transport / conformance | `TransportConformanceTest` (4) | **PASS** |
| 3 | Web V1 fixtures | `WebV1FixtureTest` (8) + `WebV1ContractComplianceTest` (16) | **PASS** |
| 4 | HTTP transport | `HttpGatewayTransportTest` (8) | **PASS** |
| 5 | Reconnect / recovery | `NetworkHardeningTest` (6), `ReconnectPolicyTest` (4), `ProcessDeathReconciliationTest` (4), LIVE recovery in `LiveAppGatewayTransportTest` (16) | **PASS** |
| 6 | Room migrations | `CursorMigrationTest` (2) | **PASS** |
| 7 | Approval readiness | `ReducerTest` approval cases + Fake e2e (`FakeGatewaySessionTest` 17) + LIVE refuse in `LiveAppGatewayTransportTest` | **PASS** |
| 8 | Security / fail-closed | `LiveHostPolicyTest` (5), `publicHostIsRejectedBeforeAnyRequest` | **PASS** |
| 9 | Debug build | `:app:assembleDebug` → `app-debug.apk` (20,705,318 B) | **PASS** |
| 10 | Release build | `:app:assembleRelease` + R8 → `app-release-unsigned.apk` (2,799,197 B) SHA-256 `DCAD1D8409C58477F601463464EEA29D8A690C423B68BD88B214167AD88E7365` | **PASS** |
| 11 | `git diff --check` | clean vs `decef003` before docs/APK copy | **PASS** |

Contract tree (`app/src/main/java/com/jarvis/android/contract/**` and `app/src/test/resources/contracts/**`) is **not** modified for this report.

## Human Check APK (same SHA)

| Field | Value |
|---|---|
| Sideload path | `dist/JARVIS-debug.apk` |
| Built from | `decef0039db8e8f8e7109ea21cf2a2191dfb8a4d` |
| SHA-256 | `2E66072237A488171CA9AA88ABBA3C0A1F5A45DAF620107C891EEAE40AC2ABFE` |
| Size (bytes) | 20705318 |
| Checklist | `DEVICE-HUMAN-CHECK.md` |
| Results template | `DEVICE-HUMAN-CHECK-RESULTS.md` |

Install **once**. Do not assemble again during the owner session.

## Blockers

- Physical S25 pass: `OWNER_DEVICE_TEST_REQUIRED` (owner present + USB/sideload)
- LIVE approvals / pairing V1 / uploads / `/api/v1` production cutover: still `WAITING_FOR_PC_A_CONTRACT` / PC-A `API_V1_ACTIVE`
- Parked stash `wip: incomplete Web V1 client hardening` is **not** applied (would move the audited baseline)
