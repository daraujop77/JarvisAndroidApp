# OWNER device verification checklist (PC-B)

One physical pass on the S25 Ultra (same tailnet, Tailscale on) closes every
`OWNER_DEVICE_TEST_REQUIRED` gate.

**Use the one-session pack** (PASS/FAIL, hashes, rollback, results template):

- [DEVICE-HUMAN-CHECK.md](DEVICE-HUMAN-CHECK.md)
- [DEVICE-HUMAN-CHECK-RESULTS.md](DEVICE-HUMAN-CHECK-RESULTS.md)

APK: `dist/JARVIS-debug.apk` built from SHA `cffcfe4764fa4ef7d6ec268ff8071c00c1e3ed4c`
(SHA-256 `BCD24B4D27686AFB7ECD9434E080DCC40C1307DFAF979520CE7F5AE8B180C961`).
Do not rebuild mid-session. Physical pass waits on PC-A recording `CROSS_SYSTEM_GATE = PASS`.

## PCB-LIVE-1 session
- [ ] Settings → IDENTITY → **Revoke & re-pair** → bottom form → Connect to Jarvis PC
      (`http://desktop-l59hjk4/`) → app relaunches, banner **ONLINE**, diagnostics
      (debug) show `TRANSPORT LIVE`, `PROTO 1.0`, `FINGERPRINT 445e4013…`.
- [ ] Wrong password → inline red error, no session stored.
- [ ] Check health button → status from PC-A `/api/status`.

## LIVE-2/3 + Lane B streaming/recovery
- [ ] Send a message → typing dots → deltas appear incrementally → done state.
- [ ] **Stop** mid-stream → bubble settles CANCELLED; send again — no duplicate text.
- [ ] Background the app mid-stream → foreground → stream finished (or recovered),
      exactly one assistant bubble with the full reply.
- [ ] Kill the app (swipe away) mid-stream → reopen → the turn reconciles with the
      server's completed answer (no second inference; check PC-A logs show one run).
- [ ] Toggle airplane mode mid-stream → OFFLINE/RECONNECTING banner → restore →
      reconnects, replay does not duplicate text.

## Lane A notifications
- [ ] Ask something slow; background the app; on completion → **one** "JARVIS
      replied" notification. Reopen and repeat — no stale re-notification after
      app restart (dedupe persisted).
- [ ] Force-kill while backgrounded with a completed reply → relaunch → notification
      does NOT fire again for a reply already in history.

## Lane B negative hosts (fail-closed)
- [ ] Pairing form URL `https://example.com` → login refused with private-network
      message, nothing leaves the phone.

## Lane E release smoke (if/when a signed release exists)
- [ ] No "Use Fake Gateway" switch, no SIMULATION card, no diagnostics, no
      "Continue with Fake Gateway" button.

## Record
For each line: PASS/FAIL + time + PC-A trace/request id when available.
Paste results into `PCB-LIVE-PROGRESS.md` under **Device evidence** and the
gates flip to `…_DEVICE_VERIFIED` (feeds PC-A PA-8 evidence).
