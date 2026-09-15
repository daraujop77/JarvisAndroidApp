# S25 Ultra Human Check — one session, one APK

**Audited code SHA (do not rebuild mid-session):** `cffcfe4764fa4ef7d6ec268ff8071c00c1e3ed4c`  
**Install this APK only:** `dist/JARVIS-debug.apk`  
(copy of `app/build/outputs/apk/debug/app-debug.apk` produced from that SHA)

Local PC-B reaudit of this SHA: `CROSS_SYSTEM_GATE = PASS` (harness only; PC-A has not recorded the gate yet). Do not start the physical pass until PC-A confirms.

**SHA-256:** `BCD24B4D27686AFB7ECD9434E080DCC40C1307DFAF979520CE7F5AE8B180C961`  
**Size:** 20,759,832 bytes  

Verify before install:

```powershell
Get-FileHash dist\JARVIS-debug.apk -Algorithm SHA256
```

If the hash does not match, **stop** — you are not on the audited APK.

This pass does **not** require a mid-session recompile. Install once, leave the APK on the phone, walk the list top to bottom. Fill `DEVICE-HUMAN-CHECK-RESULTS.md` as you go.

## Preconditions (before touching the phone)

- PC-A gateway up on the tailnet. Front door: `http://desktop-l59hjk4/`
- Phone and PC on the **same Tailscale tailnet**, Tailscale connected on both
- Owner credentials for `/api/app/login` (existing LIVE-1 surface — not pairing V1)
- USB optional (sideload from `dist/` is enough). Keep USB only if you want `adb logcat`
- Allow notifications when Android 13+ prompts (AND-W8)
- Do **not** flip to Fake Gateway after LIVE login (debug Fake toggle is for lab only)

## Install (once)

```powershell
adb install -r dist\JARVIS-debug.apk
```

Or copy the APK to the phone and install from Files. Uninstall any older JARVIS build first if the signature/version conflicts.

PASS install: app launches to Welcome → pairing/lock, no crash loop.

## What to copy from the phone / PC-A

For every LIVE chat turn, record:

| Field | Where |
|---|---|
| Phone time (local) | clock |
| Banner state | top HUD: ONLINE / CONNECTING / RECONNECTING / OFFLINE / AUTH REQUIRED / DEVICE REVOKED / UPDATE REQUIRED |
| Diagnostics (debug) | Settings → DIAGNOSTICS: `TRANSPORT`, `PROTO`, `FINGERPRINT`, `PHASE`, `CURSOR` |
| `clientRequestId` | assistant/user bubble is keyed by it; also in PC-A request log |
| LIVE `trace_id` | PC-A `/api/app/chat` request header / turn log (`X-Jarvis-*` / turn record). Phone mints a UUID per send |
| PC-A run / inference count | must stay **1** on recover-from-kill (LIVE-3) |

Do not screenshot chat if you can avoid it (`FLAG_SECURE` blanks recents/screenshots by design). Prefer handwritten IDs + PC-A logs.

## Single-session order (~25–40 min)

Do not skip ahead. Stop the session only on a FAIL that blocks later steps (login, ONLINE, or send).

### A. LIVE-1 session

| ID | Action | PASS | FAIL |
|---|---|---|---|
| L1-1 | Settings → IDENTITY → **Revoke & re-pair** → Connect to `http://desktop-l59hjk4/` with valid owner credentials | App relaunches; banner **ONLINE**; diagnostics `TRANSPORT LIVE`, `PROTO 1.0`, fingerprint starts `445e4013` | Stays pairing, crash, Fake transport, or ONLINE without a live session |
| L1-2 | Revoke again; submit **wrong password** | Inline red error; no stored session; banner not ONLINE | Session stored, silent success, or crash |
| L1-3 | Re-login correctly; tap **Check health** | Status from PC-A `/api/status` (reachable) | Timeout with no error, or public-host leak |

### B. LIVE-2 / LIVE-3 streaming + recovery

Use a prompt that streams for several seconds (e.g. “count slowly from 1 to 30”).

| ID | Action | PASS | FAIL |
|---|---|---|---|
| L2-1 | Send a message | Typing dots → incremental deltas → completed bubble | Frozen UI, no stream, duplicate bubbles |
| L2-2 | **Stop** mid-stream, then send a new message | Stopped bubble **CANCELLED**; next reply has no duplicated cancelled text | Second inference of the cancelled turn; duplicate text |
| L2-3 | Background mid-stream; wait until PC-A finishes; foreground | Exactly **one** assistant bubble with the full reply | Second bubble, truncated text, or stuck STREAMING |
| L3-1 | Swipe-kill mid-stream; reopen | Turn reconciles to the **server** completed answer; PC-A logs **one** inference | Second inference on PC-A, empty bubble, or new request id |
| L2-4 | Airplane mode mid-stream → wait for OFFLINE/RECONNECTING → restore | Reconnects; replay does not duplicate text | Retry storm, duplicate text, stuck OFFLINE while network is up |

### C. LIVE-4 / LIVE-5 profile isolation

| ID | Action | PASS | FAIL |
|---|---|---|---|
| L4-1 | If owner model chips are visible, pick a non-default profile and send | Reply uses the selected profile (PC-A turn shows that model) | Chip ignored, or app invents a catalog |
| L5-1 | Open/create conversation B; send there; switch back to A | A history unchanged; B has only B’s request ids | Cross-talk of text or request ids |

Skip L4-1 with `N/A` + note if PC-A `owner_model_selection` is off.

### D. AND-W8 notifications / background

Grant POST_NOTIFICATIONS if prompted. Use a **slow** prompt.

| ID | Action | PASS | FAIL |
|---|---|---|---|
| W8-1 | Send slow prompt; immediately background | **One** “JARVIS replied” notification when complete | Zero, or many duplicates |
| W8-2 | Open the notification; send again; force-stop; relaunch | No re-notify of the already-seen reply | Stale notification after process death |
| W8-3 | Foreground restore after W8-1 | Banner ONLINE or recovered; no duplicate assistant row | Duplicate notify + duplicate bubble |

No persistent foreground-service notification is expected (AND-W8 decision: none).

### E. Fail-closed host (Lane B)

| ID | Action | PASS | FAIL |
|---|---|---|---|
| B-1 | Pairing URL `https://example.com` + any credentials | Refused with private-network copy; PC-A / public host sees **no** login | Request leaves the phone |

Do this after LIVE tests, then **re-login** to `http://desktop-l59hjk4/` if you still need the app usable.

## Rollback / recovery if a step fails

1. **Stop.** Do not install a new APK. The session is bound to `decef003`.
2. Screenshot the HUD + diagnostics (if capture is blocked, photograph a second device).
3. On PC-A: copy the last `request_id` / `trace_id` / inference count.
4. Soft recovery (same APK): Settings → Revoke & re-pair → login again. Re-run **only the failed ID** plus L1-1.
5. If login itself is broken: uninstall, reinstall **the same** `dist/JARVIS-debug.apk`, confirm hashes, start at L1-1.
6. Record FAIL in `DEVICE-HUMAN-CHECK-RESULTS.md`. Do not mark LIVE-n DEVICE_VERIFIED.

## Out of scope this session

- Real `/api/v1` cutover
- Real approvals / pairing V1 / attachment upload
- Projects backend
- Signed release smoke (unsigned release APK is lab-only; not installed on the owner phone)

## After the session

Paste the filled results template into git (owner or PC-B). Gates `OWNER_DEVICE_TEST_REQUIRED` flip only on all LIVE-1..5 + W8 rows PASS.
