# POST_BASELINE_FUTURE_WORK

This branch is **not** the Human Check baseline.

| Frozen | Value |
|---|---|
| Human Check code SHA | `cffcfe4764fa4ef7d6ec268ff8071c00c1e3ed4c` |
| Human Check APK | `dist/JARVIS-debug.apk` |
| APK SHA-256 | `BCD24B4D27686AFB7ECD9434E080DCC40C1307DFAF979520CE7F5AE8B180C961` |

Do not replace that APK from this branch. Do not declare this HEAD as the Human Check baseline.

## What this branch adds

Remote owner-check **UI shell + local policy** (`data/remote`, `RemoteOwnerApprovalScreen`).
Not wired to live transport. No new wire fields. No Hermes path. No approve-always.

Emergency stop is a fake interface (`RemoteEmergencyStop`) until PC-A publishes a Gateway contract.

## WAITING_FOR_PC_A_CONTRACT

- Real remote approval challenge schema (screenshot, expiry, action id)
- Real emergency-stop endpoint on the authenticated Gateway
- `/api/v1` production cutover, pairing V1, upload V1
