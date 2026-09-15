# Human Check results template

Fill during the S25 Ultra session. One row per test ID from `DEVICE-HUMAN-CHECK.md`.

## Session header

| Field | Value |
|---|---|
| Date (local) | |
| Operator | |
| Device | Samsung S25 Ultra |
| Audited commit | `decef0039db8e8f8e7109ea21cf2a2191dfb8a4d` |
| APK path | `dist/JARVIS-debug.apk` |
| APK SHA-256 | `2E66072237A488171CA9AA88ABBA3C0A1F5A45DAF620107C891EEAE40AC2ABFE` |
| APK size (bytes) | 20705318 |
| PC-A baseline (if known) | |
| Front door | `http://desktop-l59hjk4/` |
| Tailscale | connected / not connected |
| Start time | |
| End time | |
| Session result | PASS / FAIL / PARTIAL |

## Results

| ID | Time | Result (PASS/FAIL/N/A) | clientRequestId | trace_id / PC-A request id | Notes |
|---|---|---|---|---|---|
| L1-1 | | | | | |
| L1-2 | | | | | |
| L1-3 | | | | | |
| L2-1 | | | | | |
| L2-2 | | | | | |
| L2-3 | | | | | |
| L3-1 | | | | | inference count on PC-A: |
| L2-4 | | | | | |
| L4-1 | | | | | |
| L5-1 | | | | | |
| W8-1 | | | | | notification count: |
| W8-2 | | | | | |
| W8-3 | | | | | |
| B-1 | | | | | |

## Diagnostics snapshot (after L1-1)

```
TRANSPORT =
PROTO =
FINGERPRINT =
PHASE =
CURSOR =
```

## Failures / rollback used

(If none: `none`.)

## Owner sign-off

- [ ] I installed only the hashed `dist/JARVIS-debug.apk` from `decef003`
- [ ] I did not rebuild mid-session
- [ ] LIVE-1..5 + AND-W8 are all PASS, or failures are listed above
