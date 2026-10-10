# Writing Room — GPT-6 code review checkpoint (2026-10-10)

## Verified upstream

- Backend main commit 9aff536320a0aa3d4a0ba52a7b704e357a51acbc: CI, production deployment, public HTTPS health and authentication perimeter passed.
- Android main commit 12f567093bbb91aa2a2aff2ed64d11dad8dff5f8: post-merge unit CI passed (run 38047415575).
- These results do not constitute a visual device acceptance test. No final APK was generated.

## High-priority findings

The main Copilot SSE endpoint still called JarvisAppSession.clear() on
locally expired bearer or HTTP 401. That erases the paired device credential,
even though ordinary Writing Room JSON endpoints already refresh the bearer.
A second issue was simultaneous refresh calls racing on a rotating credential.
Both can disrupt the main Writing Room chat, particularly after idle periods.

## Staged repair and regression tests

- Mutex-serialize refresh exchanges; reuse an already rotated bearer when
  another request has completed the refresh for the expected stale token.
- Never delete the paired credential for an expired stream or one HTTP 401.
- On an HTTP 401 from SSE, retry once with the exact same request_id; clear
  at most the short-lived access token after a second rejected attempt.
- Apply the same stale-bearer check in JSON requests.
- MockWebServer tests cover SSE unauthorized -> refresh -> completion, retained
  pairing, identical request body, and concurrent expiry -> one refresh.

## Remaining functional acceptance gates

Copilot dialogue/tools, explicit chapter approvals and immutable publication;
last-official-chapter vs future-plan distinction; Character Studio master
portrait and controlled turnarounds; visual style consistency and Wiki links;
Scene Builder and reference provenance; owner/reader permissions; reconnection
on a real Android device and UI responsiveness.

This review is a source-level preflight, not a fully observed end-to-end
device trial. No paid image generation or signed APK is authorized by it.
Pause after PR CI is triggered for the owner to monitor.

## Additional startup/reconnect finding

The initial CI (38047939289) passed. Further review found that
LiveAppGatewayTransport.connect() rejected null bearers before restore(),
and restore() never used a valid paired refresh credential on expiry.
The next commit fixes both startup paths and coalesces concurrent
no-bearer recoveries. Tests cover expired-session restore, missing-bearer
connect, and eight simultaneous no-bearer Writing Room requests.
No APK or paid image calls. Verify the next CI before integration.
