# JARVIS Android (PC-B lane)

Native Android client for JARVIS, built per `android-separate-pc-parallel-execution-plan-v1.md`.
This is the **client-development lane**: it consumes shared contracts, never redefines them.


## PC-B current execution guide

For the current Android ↔ PC-A integration phase, read first:

- [PC-B-INTEGRATION-RUNBOOK.md](PC-B-INTEGRATION-RUNBOOK.md)

Current instruction: do **not** redo AND-W0/W1/W2. Reconcile the existing Android mobile-shaped protocol to PC-A Web V1 through `PCB-R0 → PCB-R1 → PCB-R2 → PCB-R3`, then stop live integration unless PC-A reports `PA-1 = PASS`.

## Status

- **Wave AND-W0**: reproducible debug build — done (`gradlew :app:assembleDebug`)
- **Wave AND-W1**: contract models + Fake Gateway + reducer — done, deterministic tests green
- **Wave AND-W2**: conversation MVP — chat UI, Room cache, pending-outbound reconciliation,
  reconnect/resume and replay-from-cursor — done against the fake transport
- **AND-W3 pairing**: UI shell + Keystore device identity provisioning. The QR handshake and
  session credentials are **blocked on the pairing contract freeze** (plan §11)
- **AND-W4 live Gateway**: WSS transport adapter skeleton exists (`transport/wss`), blocked on
  PC-A Gateway contract. Everything else runs fully offline against fixtures
- **AND-W5 approvals/tasks**: persistent approval cards, expiry, idempotent approve/deny,
  **BiometricPrompt gate** for SENSITIVE/CRITICAL tiers, task center with progress,
  task terminal-state regression protection
- **AND-W6 media**: Photo Picker intake, EXIF/GPS-stripped private staging (re-encode),
  upload intent + attachment chips in bubbles, opaque attachment IDs (never paths/URIs).
  Binary upload transport still waits on the §11 contract freeze (upload intent wired,
  `attachment.upload` request + fake confirm/reject scenarios ready)
- **AND-W8 notifications (shell)**: channels for approvals (high, actionable Approve/Deny),
  replies, and connection state; foreground/background detection via ProcessLifecycleOwner;
  dedup by approvalId/requestId; POST_NOTIFICATIONS prompt on Android 13+
- Diagnostics UI (cursor, active requests, event notes — sanitized) run against the §7 scenario matrix

## Install on a phone (no Android Studio)

A ready-to-sideload debug APK is in [`dist/JARVIS-debug.apk`](dist/JARVIS-debug.apk).

1. On the phone: **Settings → Security → allow Install unknown apps** for Chrome / Files.
2. Download `dist/JARVIS-debug.apk` from this repo (or copy it over USB).
3. Open the APK and tap **Install**.
4. First launch: pairing screen → **Continue with Fake Gateway**. Nothing leaves the phone until a real Gateway URL is set.

USB install from a PC with platform-tools:

```powershell
adb install -r dist\JARVIS-debug.apk
```

## Build from source

Do **not** commit the Android SDK. Install it locally:

- [Android Studio](https://developer.android.com/studio) (includes SDK + JDK 21), or
- command-line SDK + a JDK 17+

Then:

```powershell
.\gradlew.bat :app:assembleDebug      # APK -> app\build\outputs\apk\debug\app-debug.apk
.\gradlew.bat :app:testDebugUnitTest  # reducer/codec/fake-transport tests
```

Gradle wrapper 8.13, AGP 8.11.1, Kotlin 2.1.21, compile/target SDK 36, min SDK 26.

## Architecture (plan-mapped)

```
com.jarvis.android
├── contract/         # §6 shared mobile-facing schemas: EventEnvelope, GatewayEvent union,
│                     #   MobileRequest, ErrorEnvelope, approval/task payloads. Additive-tolerant
│                     #   JSON (ignoreUnknownKeys), unknown event types decode to Unknown.
├── transport/        # GatewayTransport seam (§23 PrivateLinkProvider abstraction)
│   ├── fake/         # §7 Fake Gateway: deterministic scripted scenario matrix (18 presets),
│   │                 #   journal-based cursor replay, drop/resume during streaming
│   └── wss/          # AND-W4 WSS/HTTPS adapter skeleton (blocked on live contract)
├── data/
│   ├── state/        # Reducer: pure deterministic state machine (dup/stale/out-of-order/
│   │                 #   cancel-idempotency/protocol-mismatch); SessionReducer frame wrapper
│   ├── local/        # Room: conversations, messages, pending_outbound (§AND-W2)
│   ├── prefs/        # DataStore settings (non-secret only; §17)
│   └── repo/         # JarvisSessionRepository (transport+reducer loop),
│                     #   ConversationRepository (persist + process-death reconciliation)
├── security/         # AND-W3 skeleton: Android Keystore RSA identity, non-exportable
└── ui/               # Compose: pairing gate, conversations/chat (streaming bubbles, Stop),
                      #   approvals, tasks, settings (scenario picker), diagnostics
```

## Key invariants (enforced by tests, see `app/src/test`)

- duplicate `eventId` → dropped, no duplicated UI text
- out-of-order deltas → buffered, rendered contiguous in order
- cancel idempotent at every stage; exactly one cancel command reaches the transport
- kill/reconnect: in-flight requests become retryable failures, then replay-from-cursor
  revives and completes them without duplicate text (AND-W2 gate)
- protocol major-version mismatch → fails closed, visible in banner + diagnostics
- unknown/additive event types → diagnostics, never a crash
- approvals: server outcome authoritative; local resolve idempotent

## Fake Gateway scenario demo

App runs on the Fake transport by default (Settings → "Use Fake Gateway"). Pick any
scenario (Settings screen dropdown) and press "Re-run scenario now" to drive the client
through the §7 matrix without a live backend.

## Look & feel

- **Boot/welcome**: animated arc-reactor orb, staged startup lines, then a greeting
  using the name set during pairing.
- **App lock**: optional biometric / device-credential gate (Settings → Security). Re-arms
  whenever the app backgrounds so the task switcher can't leak conversations.
- **Live surfaces**: pulsing connection dot, typing dots before the first delta, a blinking
  caret while text streams, Send morphing into Stop, badge counts on Approvals/Tasks,
  animated task progress, and a drifting ambient backdrop.
- **Reduced motion**: Settings → Appearance disables looping motion, the ambient blooms and
  the boot animation. Every animated component honors it.

All looping animation is confined to the draw phase (`Canvas`/`drawBehind` reading animated
floats), so ambient motion never triggers recomposition of the surrounding UI.

## Known debt

- **Room migrations**: schemas are now exported to `app/schemas/`, but the DB still uses
  destructive fallback. A real `Migration(1,2)` + migration test is owed before any build
  that users keep data in (plan Lane C "migration tests").
- **Transport switch needs an app restart** (mode resolved once at startup).
- **No foreground service**: the socket lives only while the app is alive; server-side tasks
  continue independently, which is the intended V1 behavior (AND-W8 full scope not done).

## Not yet (blocked, per plan §11 / §22)

- live pairing handshake, auth token exchange, attachment upload contract, voice transport,
  Jarvis Secure Link / Tailscale provider wiring, foreground service sync (AND-W8),
  Projects shell (AND-W9).
