```
TASK_ID: PCB-R3
ANDROID_REPO_SHA: working tree
PC_A_BASELINE_SHA: 2a6ba24ab398356409f0448d6b13d63df8bb382a
SHARED_PROTOCOL: jarvis.web.v1 1.0
CHANGED_PATHS:
  app/src/main/java/com/jarvis/android/transport/AuthProvider.kt
  app/src/main/java/com/jarvis/android/transport/http/HttpGatewayTransport.kt
  app/src/main/java/com/jarvis/android/di/AppContainer.kt
  app/src/main/java/com/jarvis/android/transport/wss/WssGatewayTransport.kt
  app/src/test/java/com/jarvis/android/transport/http/HttpGatewayTransportTest.kt
TESTS: HttpGatewayTransportTest 4, 0 failures (MockWebServer, no live PC-A)
RESULT: PASS
EVIDENCE:
  GET  /api/v1/health
  GET  /api/v1/capabilities
  POST /api/v1/requests  (submit / resume / cancel / action)
  GET  /api/v1/events?after=<opaque_cursor>
  Opaque cursor round-trips unchanged (including not-a-number:abc+/=).
  AuthProvider injects Authorization when present; NoAuthProvider sends none (fail closed).
  Live transport mode (Use Fake Gateway off) is HTTP, not /mobile/events WSS.
  WSS adapter remains on the GatewayTransport seam but is not a production requirement.
KNOWN_LIMITATIONS:
  No credentials invented. Real token/device binding waits for PC-A pairing freeze.
  Polling interval is client-side; SSE/WSS can be added later without reducer changes.
ROLLBACK: point AppContainer live mode back at WssGatewayTransport.
NEXT_TASK: STOP — wait for PA-1
PC_A_DEPENDENCY: PA-1 = PASS plus authenticated private front door

PC_B_READY_FOR_LIVE_GATEWAY_WAITING_FOR_PA1
```
