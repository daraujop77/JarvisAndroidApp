# Writing Room — GPT-6 functional readiness checkpoint

Date: 2026-10-10 (America/Chicago).
Android main at review start: `e76df5c016386b89e7ff8c740834f4cdb1949871`.
Backend deployed and green: `af7798766be7a4029172ba3322e9bf0bbaa66a6c`.
Android main CI #38104132846: **success** after session race correction.

## Confirmed in source and automated CI

- Canon-based image prompts are scoped to project sources, keep the saved project visual style and character direction, and explicitly request a complete frontal body master.
- Copilot can propose owner-confirmed appearance changes, request image candidates, review exact asset hashes, and request derived character views.
- Character Studio maintains owner approval separately from candidate generation and Reference Pack approval; new master and views are model-pinned to GPT Image 2 Medium. Existing durable view batches retain their frozen generation mode.
- Owner confirmation now precedes bulk paid master generation; a change of project blocks stale bulk completion from mutating another project's UI.
- Android session restoration, SSE retry, and coalesced expiry recovery are covered by main CI.
- VPS deploy and external ingress passed their own CI after the backend model-selection change.

## Release blocking UI truthfulness issue

Some dashboard, Copilot, Wiki, and character directory screens previously printed `STORY-001`, `Alexander History`, chapter 37, 23 sources, and a claim that RAG/canon were synchronized even if project-scoped data was missing. This is a canon-integrity and cross-project trust issue: it presents placeholder narrative facts as verified.

The review patch replaces these with actual project story ID/title, confirmed chapter metadata, source totals from the current overview, and explicit pending/unknown states when no source has loaded. It removes ungrounded RAG-connected and synchronized badges. Character dossier's own last appearance is labeled as such, never as an invented global canon chapter.

## Acceptance still NOT proven; no final APK yet

1. On a real owner account, verify the installed UI screens on a real phone (including scene generation and acceptance in Copilot).
2. Generate **one** owner-approved paid frontal master, then review at least one derived angle visually for recognizable identity and consistent lighting/style. Do not auto-approve.
3. Verify a saved appearance change updates future candidates but does not rewrite existing approved assets or the narrative canon.
4. Verify scene grounding on canonical chapters and source citations, with no private/unpublished spoilers in child accounts.
5. Verify unseeded characters are flagged for owner design review instead of inventing appearance as story fact.
6. Review old project and newly created project screens for missing data behavior; test timeouts, interruptions and reconnect.
7. Run CI on this UI accuracy PR, merge only if green, then verify main CI. Final APK requires an explicit user review checkpoint after these checks.

Code/source acceptance **does not** establish photorealistic quality, animation, phone ergonomics or permission correctness in the deployed Android app.
