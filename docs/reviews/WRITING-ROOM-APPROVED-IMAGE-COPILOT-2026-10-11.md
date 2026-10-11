# Android approved visual read and Copilot context bridge

This batch pairs with backend `get_approved_visual_references` (2026-10-11).

Copilot shows APPROVED image cards inside chat, obtains image bytes only through authenticated project-scoped Visual Asset fetch, compares exact SHA-256 and MIME before staging, and clears project media on project switch. Only up to 6 preview assets per tool reply are fetched. Existing owner approval UI for paid image generation and canon changes remains unchanged.

Prior chat context is bounded to the most recent six locally available turns, serialized as up to 12 role/text messages with 1,200 characters per entry. Android sends history only from the current project. The backend validates shape and treats history as conversational, not official canon.

Acceptance gate: on the phone verify stored approved image previews, blocked/pending image messages, absence of cross-project thumbnail bleed, multiline responses, preserving the distinction between visually seeing pixels and listing metadata. Generating a new Android APK and publishing it must occur only after backend + Android CI and explicit acceptance of this batch.

This implementation does **not** claim that the chat's text model can interpret image pixels. It can retrieve/render approved images; a model-vision connector is separate.
