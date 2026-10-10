# A5.6 — Copilot as Character Studio turnaround coordinator

The new character turnaround intent routes `Completa las vistas de Soren` through the existing Copilot tool planner without manually toggling agent mode. Portrait requests, appearance conversation, battle illustrations and ordinary chat keep their original routes.

The owner sees the planned tool and an explicit cost warning (up to six visual generation attempts, zero auto-corrections); tapping **Completar vistas del personaje** invokes the same VPS VisualCharacterBatchStore worker as Character Studio. If the exact canonical character is ambiguous or no approved front master exists, no job begins.

Copilot shows durable batch ID and reuses its already implemented gallery. It restores verified candidate assets from the existing read-only Visual Studio batch-status/fetch endpoints and keeps final approval manual. Recovery never submits a new paid batch.

New pure intent tests, schema/wiring tests, and existing Kotlin app compilation CI. No signed APK and no provider calls in CI.

Dependency gate: merge/deploy backend only after backend CI. Confirm public VPS HTTPS before integrating Android. Later perform the combined real-device smoke test.
