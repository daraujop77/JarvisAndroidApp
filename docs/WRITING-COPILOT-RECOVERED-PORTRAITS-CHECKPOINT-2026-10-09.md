# Checkpoint — Copilot visual candidate recovery (2026-10-09)

## Context
Production Android `main` at `9d617b5` passed tests-only CI #37933256329. The Writing Room backend primary-portrait approval is deployed. Before this change, generated candidates were stored durably in Visual Asset Registry/Google Drive, but Copilot's chat preview was an in-memory `WritingWorkspaceChatTurn` and could disappear from UI after app restart or process death.

## New behavior
- On successful Writing Workspace refresh, asynchronously query authenticated `POST /api/app/writing-room/visual-assets/list` for the active project; no generative model is invoked.
- Select **up to six most recent** `PRIMARY_REFERENCE` / `front` / single-character / `CANDIDATE` assets whose provenance explicitly states `requested_from=copilot`, `operation=generation`, `model=gpt-image-2-medium`, `fallback_used=false`. Unknown model or malformed provenance is excluded.
- For an asset stored in Drive, use the authenticated `visual-assets/fetch` route and verify returned asset ID and SHA256, then verify image bytes again in private `AttachmentStore`. A failed download or hash never unlocks approval.
- Render recovered candidates as previews inside the Copilot chat. The **Recuperar retratos guardados** control offers an explicit refresh without another generation.
- Owner approval uses the existing project/character/asset/SHA-bound `approve-primary-exact` endpoint, and only when a local preview was verified and the server reports stored bytes. Already recovered cards update to approved only if Wiki linking is explicitly confirmed.
- Protect cross-project boundaries with a request epoch, clear recovered cards on project changes and verify returned `project_id`. No background polling or retries to paid image models.
- Extend portrait command parsing for colloquial Spanish e.g. `¿Me puedes generar una imagen de Naruto?`, `Quiero un retrato de Naruto`, while refusing multiple characters or unknown canonical names. Multi-person scenes are *not* converted to a single-person portrait.

## Tests
- `CopilotPortraitRecoveryPolicyTest`: strict provenance/model/scope filters; bounded gallery; candidate versus approved; pending Drive; malformed metadata.
- `WritingRoomWorkspaceApiTest`: authenticated registry list request, project binding and no generation.
- `CopilotPortraitIntentTest`: natural-language Spanish phrasings and no paid dispatch for multi-character scenes.
- GitHub Android CI must compile/test without `assembleDebug` or signed release build.

## Limitations and safety
- This is recovery of **candidates**, not a full durable transcript or historical approved gallery. Previously approved images remain in the Wiki/Visual Studio. Recovery of incomplete paid requests still depends on candidate having been saved in Visual Registry.
- The server enforces authenticated project access and owner-only approval. Nothing here changes narrative canon text, overwrites approved reference packs, generates an image automatically, or uploads new media.
- No APK build, no Android-device smoke test, no real paid image generation. Do not claim complete end-to-end use before supervised phone verification.

## Deployment gate
An isolated Android PR must pass tests-only CI before integration. Do not build an APK until a reviewed user-facing batch is ready.
