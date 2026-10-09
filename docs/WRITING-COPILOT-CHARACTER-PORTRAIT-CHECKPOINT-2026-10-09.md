# Writing Room Copilot — Character portrait generation checkpoint

Date: 2026-10-09
Branch: `feature/copilot-character-candidate-20261009`, based on the validated **PR #52** branch.
State: code staged and waiting for Android CI; no production merge, APK release or provider generation.

## Behavior

- The user sends an explicit text command such as `Genera retrato de Naruto` or `Crea una imagen de Naruto Uzumaki con uniforme` in Writing Room > Copilot.
- Android resolves the **unique existing Wiki identity** (character ID, canonical name) using a deterministic selector. It rejects unknown, colliding or multi-character names and does not guess a canonical name.
- One authenticated request is posted to the **existing** `/api/app/images/generations` endpoint, pinned to `mode=model_select`, `model=gpt-image-2-medium`. The project/character/perspective metadata uses `visual_asset.surface=character_creator`, `kind=PRIMARY_REFERENCE`, `perspective=front`, with `authority=CANDIDATE`.
- The VPS route already requires owner access and uses the Wiki, source-backed RAG physical appearance passages, owner-authored visual direction profile and any approved reference pack to ground the character before generation.
- No fallback to another image model is accepted as a verified successful result. No automatic retries: a timed-out paid request may already have generated a candidate.
- The generated image must return an exact `CANDIDATE` registry entry, a 64-digit SHA256 and the requested model, with no provider fallback. Android stage-verifies the image bytes/hash in local app-private storage and previews them directly in the chat.
- Only when the candidate is fully `stored` does the Copilot message present an explicit **Aprobar retrato como referencia visual** action. This calls `/api/app/writing-room/v2/visual/characters/approve-primary-exact` with exact `character_id`, `asset_id` and `asset_sha256`.
- The server rejects a mismatched character, SHA, angle, pending Drive storage, non-owner approval or a primary reference that would conflict with an already active visual reference pack. Successful approval registers the master as `APPROVED` and links it to the exact Wiki character; narrative canon text and chapters remain unchanged.
- If the Wiki link fails after approval, the same exact request may complete the link without running another image generation. The server blocks stale retries that could replace a newer primary.
- The Wiki-linking backend operation is prepared in backend PR #521; Android PR #53 is dependent on it being integrated and deployed.

## Tests

- Pure Kotlin tests cover unique Wiki name resolution, longest canonical name, rejection of ambiguous pairs, missing characters, and distinguishing portrait commands from normal conversation.
- MockWebServer tests verify authenticated `/images/generations` sends the pinned model, no fallback, owner-only character-creator metadata and a single character reference; exact SHA and character-binding verification for Wiki primary approval with no new image request; malformed hash is blocked locally.
- Backend tests verify authenticated exact-character approval, approved reference-pack conflicts, SHA mismatch, non-owner denial and retrying a failed Wiki link without re-approving an asset.
- Android CI should run `app:testDebugUnitTest` and `app:assembleDebug`. **No real image request or mobile smoke test has been run.**

## Dependency and blockers

1. **PR #52**: Android Copilot photo picker, attach reference candidate, preview; passed CI, not merged.
2. **PR #521** in `daraujop77/jarvis`: server-side Copilot planning/Wiki tools + separate-character-view prompt fix; passed CI, not merged. Generation already routes through the Visual Asset Registry on the existing main VPS code, but the improved single-view prompt depends on this backend PR being deployed first.
3. Generating characters without sufficient source-bound appearance and without an approved owner direction will correctly be rejected, rather than fabricate traits. Complete appearance profiles as a separate reviewed task.
4. A real Android device, authenticated owner session and working VPS Visual Registry / Codex image access are required for final end-to-end validation.
5. Test one character at a time, verify the candidate and source evidence, then approve from the chat. Do not start a visual batch automatically.

## Next

After this stacked PR passes, integrate the dependency chain in the right order and check deployment gates. Then run one explicit supervised phone smoke test: prompt -> source-bound candidate -> preview -> hash approval -> Visual Studio reference status, without changes to narrative canon.
