# JARVIS Android / VPS adaptive agent — Wiki SSE acceptance checkpoint

Date: 2026-10-13. **TEST ONLY — no production merge/deploy or APK.**

## Verified VPS evidence

Source branch/PR: \`daraujop77/jarvis\` PR #537, commit \`aff3bd5dc2b544465e64ecf2d2f4876807e2bfd6\`.

- [Real Kimi HTTP/SSE canary #38354066081](https://github.com/daraujop77/jarvis/actions/runs/38354066081): **SUCCESS**. Both offline preflights and Hermes/Kimi live read-only synthetic HTTP integration passed.
- [Backend CI #38354071225](https://github.com/daraujop77/jarvis/actions/runs/38354071225): **SUCCESS**; 112 agent tests, Docker build and VPS import.
- Loopback, synthetic Wiki, disposable database, fake HTTP session only. **Not** real Android device auth, real Wiki/canon, image inspection, production routing or public HTTPS performance.

Latest single run (not a production performance percentile):

| Scenario | First HTTP text | End-to-end | Inference detail |
| --- | ---: | ---: | --- |
| Greeting | 1,198 ms | 1,848 ms | one 1,827 ms model round |
| Wiki read | 3,677 ms | 3,677 ms | 1,600 + 2,067 ms model rounds; one \`search_story_wiki\` |
 
All three provider calls succeeded, one project Wiki read, zero writes. Final Wiki reply cited exactly one server-provided evidence ref, and the tool-grounded reply was withheld from SSE until validated.

## Android compatibility checks added

New Kotlin test \`AgentKimiWikiSseContractTest.kt\` uses a local MockWebServer that serves precisely the expected SSE event sequence and final JSON projection, with fake session credentials and synthetic Wiki only.

- Successful multi-round Wiki: \`ready\`, \`progress\`, \`using_tool\`, \`heartbeat\`, **validated answer** \`delta\`s, \`metrics\`, \`complete\`. Verify Android constructs \`WritingRoomAutoChat\`, displays answer text exactly once, keeps terminal task state \`DONE\`, shows the read-only Wiki tool and does not show fabricated images/approval UI.
- Rejected forged citation: \`BLOCKED\` terminal state + \`unverified_evidence_ref\`, no confirmation, no second inference/retry and no mistaken \`DONE\`.
- Request body carries stable (per send) \`request_id\` and account/project-scoped \`conversation_id\`; returned task is tied to the same project.
- These are **client-side contract tests**, not a guarantee about any installed or currently published APK.

## Required before release

1. Android CI compile/unit/lint must pass on PR #79 without generating an APK.
2. Backend PR #537 remains draft and adaptive VPS feature flags are OFF by default. Confirm no accidental crossover with legacy Writing Room routes.
3. Review actual authenticated HTTPS/mobile behavior, appropriate role permissions, canonical Wiki evidence and read-only restrictions, SSE interruptions and exact-request recovery.
4. Verify Writing Room visuals and approvals separately; Kimi's synthetic Wiki success does **not** establish image pixel access or canon publication capability.
5. Owner explicitly approves final merge/deployment/publishing and tests on an actual Android phone; only then build official APK.

No changes to production model selection, routes, secrets, or app publication were requested here.
