# Human Check results template

Fill during the S25 Ultra session. One row per test ID from `DEVICE-HUMAN-CHECK.md`.

## Session header

| Field | Value |
|---|---|
| Date (local) | |
| Operator | |
| Device | Samsung S25 Ultra |
| Audited commit | `cffcfe4764fa4ef7d6ec268ff8071c00c1e3ed4c` |
| APK path | `dist/JARVIS-debug.apk` |
| APK SHA-256 | `BCD24B4D27686AFB7ECD9434E080DCC40C1307DFAF979520CE7F5AE8B180C961` |
| APK size (bytes) | 20759832 |
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

- [ ] I installed only the hashed `dist/JARVIS-debug.apk` from `cffcfe4`
- [ ] I did not rebuild mid-session
- [ ] LIVE-1..5 + AND-W8 are all PASS, or failures are listed above
