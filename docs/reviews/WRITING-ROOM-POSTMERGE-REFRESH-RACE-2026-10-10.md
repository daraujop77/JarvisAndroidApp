# Writing Room: post-merge session refresh race correction

Date: 2026-10-10 (America/Chicago).

After Android PR #68 merged to main, Writing Room Android CI run
38102206355 failed during unit tests, despite passing Kotlin compilation.
The test-results artifact (11688072609) reports 305 executed, 1 failure:
`WritingRoomSessionRecoveryTest.concurrentExpiredWorkspaceRequestsRotateDeviceCredentialOnlyOnce`
observed 2 refresh calls, expected 1.

Root cause: the preflight local-expiry branch snapshots `authHeader()`
and passes that snapshot as `staleBearer` to the refresh mutex. When the
token has already been replaced by another concurrent request between
the `expired` condition and snapshot, the staleBearer equals the new
token and `refresh()` rotates the pairing credential again unnecessarily.

Correction: local-expiry/absent-token recovery calls
`refresh(reuseIfAlreadyValid = true)` with no stale bearer. This allows
all concurrent local recovery requests to share a single newly valid
bearer once they acquire the existing refresh mutex.

Importantly, reactive recovery to **real HTTP 401 responses** still
passes the exact attempted Authorization bearer as `staleBearer` so
the system refreshes if and only if that server-rejected token remains
current; it preserves the existing single-retry limit and clearing
policy if a second 401 is received.

A new mixed `restore()` + Writing Room overview concurrency test and
the existing 8-request expired-session test protect the contract.

No image generation, APK, canon mutation or production release in this
change. Gate with CI and owner-monitored workflow before integrating.
