# Writing Room Copilot A5.4 Android — automatic scene routing + edit candidates
Date: 2026-10-09. This checkpoint documents the isolated branch, **not a published APK**.

## Workflow
1. The author says `Genera una imagen de la batalla del Guardián contra Soren`. `looksLikeCopilotSceneImageRequest` recognizes **explicit** image+scene combinations and enters the existing Copilot plan without needing the "Agente con herramientas" toggle. A generic writing/battle question or single-character portrait continues through its previous route.
2. Copilot proposes `generate_story_scene` as one owner-reviewed action. The card displays engine: local or paid-capable cloud, with the existing possible-cost warning. Server resolves narrative evidence, scene context hash and approved visual references automatically. No fabricated context ID or scene text.
3. Android follows the same durable job ID through read-only `tasks/status`; the SHA-verified original `SCENE_ART` loads inline in Copilot and is restorable.
4. The author can now request **corrections on a CANDIDATE** (before approval). For candidates, `editSceneVisualAssetImage` sends an exact job ID and parent SHA to the opt-in owner-only `/api/app/images/copilot-scene-edits` endpoint. Normal approved-parent edits still use the legacy `/api/app/images/edits` endpoint with unchanged rules.
5. Each edit requires a new user click and shows possible costs. The backend enforces one durable reservation per exact request to avoid double spending and only creates a new candidate. If an HTTP result is uncertain, Android does not automatically retry. Parent lineage, source image hash and all canon associations are validated on the VPS; the current approved/candidate parent remains intact until explicit approval.
6. Exact image approval remains a separate owner gesture; it never approves story facts.

## Verification
- `CopilotSceneRequestsTest`: explicit image battles route automatically, prose/portraits do not.
- `CopilotAgentWiringTest`: correct candidate endpoint, job ID, cost gate and both tool names.
- Previous candidate ancestor selector tests still apply.
- Run Kotlin compile and unit tests only, **no APK per workflow**.

## Notes
Only after backend PR is merged, deployed and externally healthy should Android PR be merged. Candidate edits may generate cloud charges **only** after an explicit author click. No provider calls in unit tests. Real device smoke and final APK remain a later, author-reviewed checkpoint.
