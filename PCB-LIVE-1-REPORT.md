```
TASK_ID: PCB-LIVE-1
ANDROID_REPO_SHA: (this commit)
PC_A_BASELINE_SHA: d940c45095bd3a6d67cee1650e05ffffae712df1 (jarvis main, cloned)
SHARED_PROTOCOL: jarvis.web.v1 1.0 (fingerprint 445e4013df96ad986232eaecec6c58c01bab472e2a55c5ec11c54536de552021);
                 live surface = PC-A app-session API (/api/app/*) until /api/v1 is served
CHANGED_PATHS:
  app/src/main/java/com/jarvis/android/transport/live/JarvisAppSession.kt (new)
  app/src/main/java/com/jarvis/android/transport/live/LiveAppGatewayTransport.kt (new)
  app/src/main/java/com/jarvis/android/di/AppContainer.kt (LIVE mode; resolveMode)
  app/src/main/java/com/jarvis/android/ui/JarvisViewModel.kt (liveLogin/liveLogout + cold relaunch)
  app/src/main/java/com/jarvis/android/ui/screens/PairingScreen.kt ("Connect to Jarvis PC")
  app/src/main/java/com/jarvis/android/data/repo/JarvisSessionRepository.kt (connect failure no longer kills collector)
  app/src/main/java/com/jarvis/android/contract/webv1/WebV1.kt|WebV1Adapter.kt (official envelope: schema, sequence,
      payload.delta, SHA-256 fingerprint, trace_id/idempotency_key/target_request_id)
  app/src/test/resources/contracts/web-v1/** (REPLACED with official PC-A files)
  app/src/test/java/com/jarvis/android/contract/webv1/WebV1FixtureTest.kt (now consumes official fixtures verbatim)
  app/src/test/java/com/jarvis/android/transport/live/* (new: host policy + MockWebServer E2E)
TESTS: 71 unit tests, 0 failures (9 suites); :app:assembleDebug BUILD SUCCESSFUL
RESULT: PASS (fixture-level). Live phone run pending owner action below.
EVIDENCE:
  - Fake transport and live transport now both speak the same wire: Fake Gateway emits
    official Web V1 envelopes; tests consume PC-A's checked-in fixtures unmodified.
  - Live adapter mirrors PC-A's own web client (auth.js): POST /api/app/login (Bearer
    token, only token persisted, never password), GET /api/app/session probe, SSE
    /api/app/chat/stream (ready|delta|complete|error) translated to internal
    EventEnvelope so the existing reducer gives dedupe/cancel/streaming unchanged,
    scoped POST /api/app/chat/cancel, GET /api/app/chat/requests/{id} recovery with
    X-Jarvis-* headers, private-host fail-closed URL policy (loopback/RFC1918/
    100.64/10/*.ts.net/MagicDNS single-label; public rejected pre-request).
  - MockWebServer E2E: stream->completed, cancel idempotence (<=1 wire command),
    expired session fails closed without sending the token, unauthenticated link dead.
  - Bug found+fixed by the tests: restore() sent the raw token instead of
    "Bearer <token>" (would have failed 401 on the phone every connect).
KNOWN_LIMITATIONS:
  - PC-A does not serve /api/v1/* yet (confirmed on d940c45 + jarvis-web.ps1);
    approvals/attachments deliberately refuse on the live surface (PA-5/6 + upload freeze).
  - desktop-l59hjk4 is MagicDNS: resolvable only on the tailnet. PC-B has no Tailscale,
    so end-to-end proof runs on the PHONE (per runbook §10 path phone->tailnet->front door).
  - Transport switch = cold restart (existing V1 limitation; login triggers relaunch).
  - HTTP mode (frozen /api/v1 adapter, PCB-R3) stays wired for when PC-A serves it.
ROLLBACK: drop AppContainer LIVE wiring; fixtures/adapter changes are additive on R0-R3.
NEXT_TASK: owner runs the phone test below; then PCB-LIVE-2 (stream/cancel/reconnect
  observation through the overlay, feeding PA-8 evidence).
PC_A_DEPENDENCY: none for LIVE-1; PA-5/6 gates approvals; /api/v1 serving for PCB-R3 mode.
```

## Phone procedure (owner)
1. Tailscale on the phone: signed into the same tailnet, reachability to PC-A confirmed
   (PA-8 already saw the Android peer).
2. Install the new debug APK (or I adb-install it).
3. Pairing screen → **Connect to Jarvis PC**: URL `http://desktop-l59hjk4/` (or
   `http://100.95.123.102:8787`), your JARVIS username/password → sign in.
4. App cold-restarts onto the LIVE transport; chat streams from PC-A models;
   Stop cancels; killing the app mid-stream then reopening reconciles via the
   pending-outbound + recovery paths already under test.
