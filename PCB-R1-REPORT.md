```
TASK_ID: PCB-R1
ANDROID_REPO_SHA: working tree (uncommitted with R2/R3)
PC_A_BASELINE_SHA: 2a6ba24ab398356409f0448d6b13d63df8bb382a (runbook; jarvis repo not readable)
SHARED_PROTOCOL: jarvis.web.v1 1.0 / fingerprint web-v1-1.0
CHANGED_PATHS:
  app/src/main/java/com/jarvis/android/transport/fake/FakeGateway.kt
  app/src/main/java/com/jarvis/android/contract/webv1/WebV1Adapter.kt (domainToWire)
  app/src/main/java/com/jarvis/android/contract/Requests.kt (Replay.sinceCursor: String)
  app/src/main/java/com/jarvis/android/data/repo/JarvisSessionRepository.kt
  app/src/test/java/com/jarvis/android/data/repo/FakeGatewaySessionTest.kt
TESTS: FakeGatewaySessionTest 16, 0 failures (Web V1 frames on the wire)
RESULT: PASS
EVIDENCE:
  Fake Gateway serializes WebV1Event via WebV1Adapter.domainToWire.
  Happy-path frames contain event_id / protocol_version / opaque tok_* cursor, not nested event{}.
  Duplicate event_id does not duplicate UI text.
  Replay from opaque cursor (and from "") does not duplicate already-delivered text.
  Cancel remains idempotent (exactly one cancel command).
  Protocol mismatch (2.0 / web-v2) fails closed.
  Additive optional hologram.started is ignored + diagnostic; stream still completes.
  Scenario matrix preserved, plus OPTIONAL_UNKNOWN.
KNOWN_LIMITATIONS:
  Fixtures remain vendored from the runbook (PC-A repo still private).
ROLLBACK: revert FakeGateway emit path to EventEnvelope JSON.
NEXT_TASK: PCB-R2
PC_A_DEPENDENCY: none for this packet
```
