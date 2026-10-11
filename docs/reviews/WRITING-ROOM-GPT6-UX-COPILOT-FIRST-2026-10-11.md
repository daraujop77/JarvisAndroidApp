# Writing Room — GPT-6 integrated UX review (before APK)

Reviewed: October 11, 2026.
Android starting point: `main` commit `01e59795f61aa3726a6cf45baea4a95e992b7181`.
Android postmerge CI: [#38116847717](https://github.com/daraujop77/JarvisAndroidApp/actions/runs/38116847717), success.
Backend VPS deployment: `af7798766be7a4029172ba3322e9bf0bbaa66a6c`, verified in previous checkpoint.

## Product intent

- Copilot is the primary conversation and operating surface. Users describe a character, scene, chapter, or visual change once, then receive a visible proposal/candidate and approve exact results.
- The other tabs (Write, Wiki, Visual Studio, Plan, Canon, Library) are supporting tools, not prerequisites the user must manually navigate in order to initiate work.
- No fake canonical facts, no cross-project visual/session state, no silent paid generations or automatic canon approvals.
- One accepted frontal full-body identity master should anchor further character views, with the saved project visual style used consistently.

## Source audit — concrete findings fixed together

1. **Wrong default landing:** Writing Workspace opened on Overview regardless of the Copilot-first design goal. Default to Chat; scope the selected tab to `projectId` to prevent inheriting another project's active tab.
2. **Cross-project draft leakage:** The Copilot composer and several Write, Plan, Council, Wiki, and Character Directory `rememberSaveable` fields were not keyed by project. Key draft/navigation/selection state by `projectId`, retaining chapter or character subkeys where needed.
3. **Inconsistent paid approval:** Character Studio's single master, one-angle generation, and full turnaround were directly dispatched while only bulk masters had a confirmation. Provide separate dialogs stating provider, paid nature, possible multiple generated outputs/corrections, candidate-only status, and cancel.
4. **Scene Director could start cloud generation automatically:** The resolution endpoint's READY branch starts a durable job. Both the initial request and selecting an ambiguous passage now require an explicit owner confirmation. The UI no longer describes passage selection as merely "Use this passage" when it can generate images.
5. **Manual Cloud scene generation:** Ask confirmation before cloud requests; preserve the separate explicit local execution path (which does not silently fall back to Cloud).
6. **Late response scope:** Manual Scene Builder calls and durable character turnaround setup were not protected against navigation while waiting for a provider. Capture scope versions and verify before mutating current UI, preserving durable server jobs and deleting only newly staged stale thumbnails.
7. Add static regression tests for Copilot-first launch, project-specific composition, paid confirmation placement, and response scoping. These complement—not replace—device-level tests.

## Remaining acceptance — STOP before final APK

- **User review:** inspect actual on-device chat flow, tab layout, input focus/keyboard, scroll and long streaming messages. Static source checks cannot establish screen ergonomics.
- **One expressly authorized paid image:** full-body, front view, no cropped limbs, correct canonical description and approved style. Approve only after preview.
- **Derived view:** compare face, silhouette, outfit and render style to approved master, and verify candidate/approval flow.
- **Scene from a real canon passage:** audit evidence/characters and compare visual references; no unpublished future plans shown as occurred facts.
- **Child/reader:** verify no paid generation, approval, or private planning access even with retained sessions.
- **Device recovery:** navigate between two projects with unsent drafts and jobs in flight; test reconnect from expired bearer and background/resume.
- **Explicit approval before creating APK:** the next mobile build must not be treated as final user acceptance.

## Workflow gate

This branch contains the related UI and ViewModel edits in one PR. No provider calls, paid images, document mutations, canon approvals, APK build, or direct push to main. Run Android unit/Kotlin CI, stop and let the owner monitor. After green CI, merge in a subsequent owner checkpoint and reverify main before offering an APK of the reviewed UI.
