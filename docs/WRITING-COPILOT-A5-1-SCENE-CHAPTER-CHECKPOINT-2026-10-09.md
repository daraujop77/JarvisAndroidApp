# A5.1 Android — visual scene preparation and chapter status through one Copilot card (2026-10-09)

## Scope
The same `CopilotTaskState` card (introduced in PR #57) now handles two new backend capabilities without opening another transport/router/agent module: `get_chapter_workflow` and `prepare_visual_scene`.

### User flow
In Writing Room Copilot enable **Agente con herramientas**. Ask, for example:
- «Revisa el estado y las revisiones del capítulo que estamos escribiendo».
- «Prepara el contexto visual de la batalla entre el Guardián y Soren basándote en el canon; todavía no generes imagen».

The VPS model generates a typed, validated task. Chapter status is owner-only read and can execute automatically if the owner is authorized. Scene preparation is an owner-only `PROPOSED_ONLY` action; its exact parameters and the **Preparar escena** button are presented in chat, *without any image generation*. The existing Scene Director checks written canon and approved visual references, freezes context and returns `context_id`, hash, readiness and ambiguity candidates.

### Contract compatibility
`CopilotTaskStep.effect` is separate from `access`. Older task envelopes can omit it; the Android serializer defaults to READ_ONLY. For any proposal that affects persistent state, `requires_confirmation` is authoritative from VPS; UI shows the full arguments before approval. Existing `tasks/latest` and `tasks/status` recover results after Android process death, without re-dispatch.

### Validation
- Kotlin source test checks recognition and display of new tool names, effect semantics and scene confirmation label.
- Backend independently tests ACL owner, hash/passages, no image provider call, and durable one-time execution.
- No APK created in this feature PR. Phone/provider smoke test happens after backend is deployed and the next integrated release is requested.
- No Tailscale routing or new paid API credentials.
