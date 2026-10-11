# Writing Room: paid portrait consistency and project scope review

Review date: 2026-10-10. Based on the current merged Android commit e0e1d89903be07b05fcc7a7e73d805473e610b93.

Problem: Copilot's owner-confirmed portrait path pinned GPT Image 2 Medium,
but Character Studio's individual and bulk master generation still used
quality mode, which was eligible to route to another provider or silently
fall back. This can produce visible art-style drift across approved references.

Changes staged together:
1. Character Studio's single manual character generation and bulk front
   masters use model_select with gpt-image-2-medium, consistent with Copilot.
2. New character turnaround batches initiated from Character Studio and
   Copilot use model_select; old durable batches resume with their original
   frozen generation mode, never silently rewriting in-flight contracts.
3. Bulk generation shows an explicit dialog with the live number of candidate
   images and their paid-model identity. Confirm count is verified again on
   dispatch; owner only. No image is approved automatically.
4. When a bulk provider request finishes after navigation to a different
   project/character, the old request cannot mutate new project's UI state.
5. Regression wiring test verifies model pin, confirmation, owner/count gate
   and old-batch mode preservation.

Backend companion PR must freeze gpt-image-2-medium in requests when
creating a model_select turnaround batch. Keep both changes reviewed
together before merging. This is not a paid image test or a real-device
visual acceptance. No final APK.
