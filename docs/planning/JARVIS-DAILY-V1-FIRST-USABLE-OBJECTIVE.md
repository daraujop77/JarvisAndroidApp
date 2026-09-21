# JARVIS Daily v1 — First Usable Objective

> **Android repository mirror**
>
> This file mirrors the Daily v1 plan from the control-plane repository `daraujop77/jarvis`, source branch `planning/jarvis-daily-v1-objective-20260920`.
> The control-plane copy remains the planning source of truth. This mirror exists so Android agents can work from the same milestones without depending on chat history.
>
> **Android current-state note (2026-09-21):**
> - canonical Android repo: `daraujop77/JarvisAndroidApp`
> - active self-update branch: `feature/self-update-v1-20260921`
> - current self-update HEAD: `89d2ca2a5ed83d718bcbc1310c3672b6f47c15ae`
> - current Android blocker under investigation: LIVE session shows `CONNECTING -> DISCONNECTED`; VPS diagnostics show the VPS healthy and no authenticated Android requests in the recent backend log window
> - PC-4 remains parked
> - after Android LIVE E2E is restored, proceed toward M2 multi-session
>
> Do not treat this mirror as permission to merge, deploy, or activate production changes without the normal owner/review gates.

---

**Status:** DRAFT FOR OWNER REVIEW — DOES NOT AUTHORIZE IMPLEMENTATION OR PRODUCTION ACTIVATION  
**Date:** 2026-09-20  
**Branch:** `planning/jarvis-daily-v1-objective-20260920`  
**Purpose:** Rebaseline the first product milestone around a usable daily JARVIS instead of waiting for the full long-term system.

---

## 1. Executive goal

The first major product objective is now:

> **JARVIS Daily v1 = a fluid native Android JARVIS with multiple isolated sessions, a functional Writing Room, persistent project/context state, and one stable VPS entry point that routes work to cloud or the home PC.**

This is intentionally smaller than the complete JARVIS vision and larger than a simple chat MVP.

The objective is considered complete when the owner can use JARVIS every day from Android without changing endpoints or thinking about where models run, can maintain multiple independent conversations/tasks, and can open a Story/Writing workspace that uses canon-aware retrieval and a Council without allowing agents to silently modify canon.

This draft changes prioritization only. It does not delete PC-control, Blender, voice, family support, or other long-term goals.

---

## 2. Priority rebaseline

Previous planning treated Story Workspace / Writing Room as a later feature after additional PC-control and memory stages.

The new proposed priority is:

1. Stable VPS control plane / authoritative router.
2. Multi-session foundation.
3. Reliable Phone -> VPS -> cloud and Phone -> VPS -> PC paths.
4. Persistent conversation/project state.
5. Writing Room + canon/RAG foundation.
6. Council MVP.
7. Android UX stabilization.
8. Reliability/soak and Daily v1 acceptance.

PC-4 remains parked and must not block Daily v1.

Computer Use, broad PC autonomy, Blender automation, full voice, advanced overlay, family concurrency, and other large features resume after Daily v1 unless they are required to unblock a Daily v1 acceptance gate.

---

## 3. Architectural decision proposed for Daily v1

### 3.1 One normal entry point

Both primary clients use the VPS as their normal application endpoint:

```text
JARVIS Phone ─┐
              ├──> VPS / JARVIS Control Plane / Router
JARVIS PC UI ─┘                │
                               ├──> Cloud model/Hermes lane
                               │
                               └──> PC Worker over private Tailscale link
                                          ├── local LLMs
                                          ├── local files/projects
                                          ├── future Blender
                                          └── future Action Gateway / Computer Use
```

The Android app and the PC UI should not contain provider-specific routing logic.

A client may express a preference such as `fast`, `normal`, `deep`, `local`, or `cloud`, but the VPS remains authoritative for whether a route is permitted and where it executes.

The phone must not need a different URL when:
- the selected model changes;
- the PC reboots;
- a cloud provider changes;
- a local model is unavailable;
- the owner moves between Wi-Fi and mobile data.

### 3.2 PC becomes a worker, not the mobile front door

The Windows PC remains the important high-capability machine, but for Daily v1 it is modeled as a worker behind the VPS.

The VPS should know whether the PC worker is:
- online;
- offline;
- degraded;
- busy;
- capable of local inference;
- capable of approved project/file operations;
- capable of later typed PC actions.

If the PC is offline, cloud chat and VPS-hosted project state should remain usable. Requests that truly require the PC should fail clearly as `PC_WORKER_OFFLINE` or equivalent rather than silently rerouting to an unsafe or semantically different path.

### 3.3 Explicit offline-PC fallback is separate

A future local-only fallback may allow the PC UI to talk directly to local models if the VPS/Internet is unavailable.

That is not the normal Daily v1 architecture and should not create a second divergent source of truth.

---

## 4. Daily v1 scope

Daily v1 has three product pillars.

### Pillar A — Multi-session JARVIS

The owner must be able to run multiple independent conversations/tasks without state leakage.

Initial product target:

- up to **3 phone-side active conversations/tasks plus 1 PC-side task**;
- no requirement for four simultaneous DEEP generations;
- DEEP parallel remains 1 unless benchmarks justify otherwise;
- short/fast requests should remain responsive while a longer task exists;
- conversation lifecycle and task/run lifecycle remain distinct.

Required identifier chain:

```text
user_id
device_id
session_id
conversation_id
request_id
trace_id
task_id / run_id when applicable
```

Acceptance requirements:

- two conversations never share transcript/context accidentally;
- reconnect does not duplicate a request;
- cancel is scoped to the correct request/conversation;
- one long task does not corrupt another chat;
- Android and PC can resume authoritative state from VPS;
- request results are idempotent/recoverable;
- PC writes remain serialized where required;
- cloud/local route decisions are logged at metadata level.

Daily v1 is OWNER-first. Full family roles and cross-user permissions remain later work, but the data model must preserve the existing user/session isolation design so family support does not require a rewrite.

---

## 5. VPS control plane requirements

The VPS is the authoritative coordination layer for Daily v1.

It should own or coordinate:

- authentication/session validation;
- conversation registry;
- request/task state;
- router decisions;
- worker availability;
- queueing;
- streaming relay;
- project/workspace registry;
- Writing Room session state;
- authoritative server-side route policy;
- reconnect/recovery state;
- audit metadata;
- future scheduled jobs.

The VPS should **stream through**, not buffer full model answers before forwarding.

Preferred behavior:

```text
model/PC delta
    -> VPS receives
    -> VPS immediately relays
    -> Android/PC UI renders
```

Routing should be deterministic where possible and model-assisted only where useful.

Daily v1 should avoid infrastructure sprawl. No Kubernetes, Kafka, distributed broker, or complex WFQ is required for the target scale.

---

## 6. PC Worker contract

Daily v1 needs a narrow, stable VPS <-> PC worker boundary.

The worker should expose capabilities, not arbitrary implementation details.

Example capability advertisement:

```json
{
  "worker": "home-pc",
  "status": "online",
  "capabilities": {
    "local_inference": true,
    "project_files": true,
    "blender": false,
    "computer_use": false
  }
}
```

For Daily v1, the worker's critical capability is local inference plus any read-only/project operations needed by Writing Room if the authoritative project source remains on the PC.

Long term, Writing Room source-of-truth should not depend on the PC being powered on. Published canon/project state should be available to the VPS control plane or another durable canonical store.

PC-4 and unrestricted GUI mutation remain outside this milestone.

---

## 7. Model routing for Daily v1

Clients should request product-level modes, not providers.

Preferred client-facing concepts:

- Auto
- Fast
- Normal
- Deep
- Local preferred
- Cloud preferred, if policy allows

The VPS resolves those concepts to the current model/provider/worker.

Examples:

```text
simple chat
-> VPS Auto
-> cloud fast/normal route or local fast route according to policy

"usa mi modelo local"
-> VPS checks PC worker
-> local inference if online and permitted

complex story architecture
-> Writing Room policy
-> deep local and/or cloud specialist route

PC-specific task
-> PC worker required
```

No Android code should hard-code `xai`, `grok`, `ollama`, `hermes`, or a physical PC destination as the authority for normal routing.

Provider/model details may still be displayed diagnostically.

---

## 8. Writing Room product goal

Writing Room is part of Daily v1, not a decorative future shell.

The first real workspace is the existing story project already described in:

- `docs/planning/features/STORY-WORKSPACE-COUNCIL-ENGINE.md`

The implementation should be reusable for future writing projects.

Primary navigation target:

```text
Projects
  -> Story Project
      -> Writing Studio
      -> Council
      -> Canon
      -> Lore / RAG
      -> Characters
      -> Chapters
      -> Decisions
```

Daily v1 does not require every future Story Workspace feature.

---

## 9. Writing Room MVP

Daily v1 Writing Room must provide:

### 9.1 Project state

Each writing project has a durable `project_id`.

At minimum the system tracks:

- title;
- project metadata;
- authoritative canon version;
- chapters/scenes;
- characters;
- canon facts;
- open threads;
- decisions;
- source documents;
- Council sessions.

### 9.2 Authority classes

The existing authority design is retained.

Important states include:

- OFFICIAL_CANON
- AUTHOR_SECRET
- CHARACTER_KNOWLEDGE
- LOCKED_FUTURE
- APPROVED_PLAN
- PROPOSED
- DRAFT
- REPLACED
- REJECTED
- UNCERTAIN
- SOURCE_FRANCHISE_CANON

RAG ingestion never means canon promotion.

### 9.3 Human authority

No model, specialist, character simulation, Council vote, or background worker may directly promote content to canon.

Required promotion concept:

```text
IDEA
-> COUNCIL CANDIDATE
-> HUMAN SELECTED
-> APPROVED PLAN
-> WRITTEN DRAFT/CHAPTER
-> HUMAN APPROVED
-> CANON
```

Daily v1 may simplify UI around these states, but not the authority rule.

---

## 10. RAG and story memory

Daily v1 should not put the complete story into every context window.

Use the existing hybrid design:

1. Compact Canon Kernel.
2. Structured story state.
3. Narrative RAG.
4. Hierarchical summaries.
5. Council/session memory.

The first implementation may use SQLite plus a derived semantic/vector index.

The canonical source and the derived index must be distinguishable.

A vector database/index is disposable/rebuildable. It is not canon.

Retrieval should prefer:

1. explicit locked owner decisions;
2. official chapter text;
3. canon/continuity rules;
4. structured state derived from official sources;
5. approved plans;
6. locked future events;
7. summaries;
8. drafts/proposals.

Every retrieved chunk should carry enough metadata to identify authority and source.

---

## 11. Council MVP

Daily v1 includes a live Council, but not the entire future Council roadmap.

Required participants:

- JARVIS Moderator;
- Showrunner;
- Canon Keeper;
- Challenger;
- selected character simulations.

Character identities must remain independent of model provider.

A character is:

```text
persona
+ current story state
+ relationships
+ scoped knowledge
+ retrieved relevant scenes
+ permissions
```

not "a permanent GPT/Grok/Gemini bot."

Required Council behavior:

- streaming;
- @mentions;
- user can request a specific participant;
- not every participant answers every turn;
- local/fast routing can choose SILENT / SHORT / FULL / CHALLENGE / CANON_WARNING;
- important questions can escalate to deeper specialists;
- final decisions remain human-controlled;
- session summary and decisions persist.

Independent-round-before-debate is recommended for major decisions but can be implemented after the basic Council path works.

---

## 12. Writing Studio MVP

The Writing Studio needs to support actual production work, not only chat.

Daily v1 minimum:

- current chapter/scene draft;
- relevant canon context;
- active characters;
- open threads;
- retrieved source evidence;
- optional continuity warnings;
- handoff from an approved Council plan;
- save/resume draft state.

Diagnostics may initially be limited to:

- CANON
- CHARACTER KNOWLEDGE
- TIMELINE
- OPEN THREAD

Warnings must be non-destructive.

Automatic rewriting is opt-in only.

---

## 13. Android Daily v1 acceptance

The native Android app is the primary Daily v1 interface.

The real Android repository is:

`daraujop77/JarvisAndroidApp`

The backend/control-plane repository is:

`daraujop77/jarvis`

These repositories must remain distinct.

Android acceptance requirements:

- one stable VPS endpoint;
- authenticated session recovery;
- conversation list;
- multiple conversations;
- streamed responses;
- no forced scroll-to-bottom when the user is reading older content;
- auto-follow resumes when the user returns to the bottom;
- cancel works;
- reconnect works without duplicate messages;
- process/background recovery preserves useful state;
- Markdown/code is readable;
- composer/keyboard behavior is stable;
- Writing Room project list;
- Council chat;
- Writing Studio basic surface;
- visible connection/worker state;
- clear errors for cloud unavailable vs PC worker unavailable.

The app must remain a client, not a second router or memory authority.

---

## 14. Current reusable foundations

This plan should reuse existing work rather than restart it.

Existing foundations already present in the project include:

- Android native app and Room persistence;
- Android SSE transport and incremental deltas;
- request/session/conversation IDs;
- reducer sequencing/deduplication;
- cancel/recovery concepts;
- multi-session/concurrency planning and tests;
- Tailscale private connectivity;
- Windows local model runtime;
- Hermes integration;
- explicit model profiles;
- request tracing;
- existing Story Workspace / Council design;
- existing multi-user decision and isolation design.

Before implementation, agents must inspect the current branch/repo state and must not assume that an older planning snapshot describes the current code exactly.

---

## 15. Delivery milestones

### M1 — VPS authoritative router

Goal:
Phone and PC UI use a stable VPS endpoint. VPS is authoritative for routing.

Gate:
- Phone -> VPS -> cloud works with streaming.
- Phone -> VPS -> PC local model works with streaming.
- same Android endpoint for both.
- route metadata observable.
- no provider/destination authority in Android.

### M2 — Multi-session foundation

Goal:
Multiple independent conversations/tasks.

Gate:
- at least 3 phone conversations/tasks + 1 PC-side task can coexist logically;
- no cross-conversation state;
- cancel/reconnect scoped correctly;
- long task does not corrupt short chat;
- authoritative recovery from VPS.

### M3 — Durable project/workspace service

Goal:
Projects become first-class server-side objects.

Gate:
- project list/create/open;
- durable project_id;
- workspace sessions survive app restart;
- Android can navigate Projects -> Writing Room.

### M4 — Story data + RAG

Goal:
Canon-aware retrieval.

Gate:
- canonical sources imported;
- authority metadata enforced;
- semantic retrieval returns source metadata;
- drafts cannot silently outrank canon;
- index can be rebuilt from authoritative state.

### M5 — Council MVP

Goal:
Real interactive writers' room.

Gate:
- Moderator + Showrunner + Canon Keeper + Challenger;
- selected character participants;
- @mentions;
- streaming;
- session state;
- decisions/proposals persisted;
- no direct canon write.

### M6 — Writing Studio MVP

Goal:
Use Council output to write.

Gate:
- editable/resumable draft;
- relevant canon/context side information;
- approved-plan handoff;
- basic continuity diagnostics;
- explicit human approval path.

### M7 — Android polish and reliability

Goal:
Make Daily v1 pleasant enough for normal use.

Gate:
- streaming feels immediate;
- scroll/composer stable;
- Markdown readable;
- reconnect/background/resume correct;
- error states useful;
- no duplicate turns;
- repeated daily-use test passes.

### M8 — Daily v1 acceptance

Run real E2E scenarios from Android and PC.

Minimum acceptance scenarios:

1. Cloud chat while PC is offline.
2. Local-model chat while PC is online.
3. Three separate phone conversations remain isolated.
4. A long request and short request coexist correctly.
5. Kill/reopen Android and resume.
6. Network drop/reconnect during a turn.
7. Open Writing Room.
8. Ask a canon-grounded question and inspect source authority.
9. Open Council and request two different specialist perspectives.
10. Approve a plan without promoting arbitrary model output directly to canon.
11. Resume Writing Studio later.
12. PC worker goes offline without breaking cloud-only JARVIS.

Daily v1 is accepted only after these scenarios are evidenced, not because code exists.

---

## 16. Explicit non-goals for Daily v1

Not required to declare Daily v1 usable:

- PC-4 / unrestricted real mouse and keyboard control;
- full Computer Use;
- Blender autonomy;
- StarCraft II automation;
- full voice mode;
- cinematic overlay;
- family accounts;
- CHILD profile rollout;
- four simultaneous DEEP generations;
- automatic provider rotation;
- public Internet ingress;
- replacement of Tailscale;
- Scene Room;
- voting UI;
- scheduled Council meetings;
- persistent Grok Bot;
- every character implementation;
- fully autonomous canon updates.

These remain future work unless a later owner decision promotes one into the gate.

---

## 17. Performance targets to measure

Do not treat these as PASS until measured.

Track separately:

- Phone -> VPS network RTT.
- VPS -> PC worker RTT.
- routing decision time.
- model TTFT.
- end-to-end TTFT.
- streaming jitter.
- queue wait.
- reconnect recovery time.
- duplicate-request count.
- cross-session leakage count.
- RAG retrieval latency.
- Council first-speaker latency.

Architectural target:
VPS forwarding overhead should be small compared with model inference and should not buffer complete responses.

A material unexplained VPS overhead, for example hundreds of milliseconds beyond network/handshake costs, is an optimization bug to investigate rather than an accepted design feature.

---

## 18. Security and correctness constraints

Daily v1 does not weaken existing safety boundaries.

- VPS authenticates clients.
- PC worker accepts only authenticated/authorized control-plane requests.
- Model/provider credentials never live in Android.
- Android cannot authorize its own cloud use.
- PC actions remain behind typed policy boundaries.
- No new public port-forwarding.
- Tailscale/private overlay remains the current network boundary.
- Secrets are excluded from logs.
- Project/canon authority is server-side.
- Cross-session and future cross-user isolation must be testable.
- A model response is never proof that an action executed.

---

## 19. Source-of-truth strategy

Daily v1 must avoid two divergent memories.

Target principle:

> **Authoritative project state is durable and centralized; vector indexes and caches are derived.**

Git may remain an authoritative publication/versioning surface for selected project/canon artifacts.

The VPS may host the control-plane database and derived RAG index.

The PC may hold local working files and high-capability tools.

The exact storage split must be reviewed before M3/M4 implementation, but the architecture must never require manually synchronizing two independent canon databases.

---

## 20. Review questions before implementation

Owner/reviewer should explicitly answer:

1. Is VPS-as-single-normal-entry-point approved?
2. Should PC UI also normally route through VPS, with local direct mode only as explicit offline fallback?
3. Is OWNER-only Daily v1 acceptable before family accounts?
4. Is the target of 3 phone tasks/conversations + 1 PC task sufficient for Daily v1?
5. Is scheduled Council correctly deferred until after live Council works?
6. Which project is the first Writing Room production project?
7. Where should authoritative canon live for Daily v1: Git files, VPS database, or a defined hybrid?
8. Is SQLite on VPS acceptable for initial structured story state?
9. Which embedding/vector implementation should be used for the first RAG index?
10. Which model modes should Android expose: Auto/Fast/Normal/Deep only, or Local/Cloud preferences too?
11. Should a cloud route remain usable when PC is offline?
12. What exact Android UX is required before calling the app "fluid"?
13. Is Markdown/code rendering a Daily v1 blocker?
14. Which Council characters are required in the first production test?
15. Should Council decision capture use one owner approval or support co-creator approval in v1?
16. What data may leave the home PC for cloud story reasoning?
17. Should project attachments be stored on VPS, PC, or both with one authority?
18. What retention/audit data should Council sessions keep?
19. What is the rollback behavior if VPS is temporarily unavailable?
20. Which existing Stage 3/PC-control tasks are explicitly paused until Daily v1 acceptance?

---

## 21. Related existing planning documents

This draft should be reviewed together with:

- `docs/planning/features/STORY-WORKSPACE-COUNCIL-ENGINE.md`
- `docs/planning/architecture/MULTI-USER-INTEGRATION-DECISION-V2.md`
- `docs/planning/android/ANDROID-APP-IMPLEMENTATION-V1.1.md`
- `docs/planning/current/07-DECISION-LOG.md`
- `docs/planning/current/06-TESTS-QA-BENCHMARKS-V2.2.md`
- `docs/planning/architecture/PRODUCTION-ACTIVATION-V1.md`

Where older documents state that Writing Room must wait until after Stage 3 PC Control, this draft intentionally proposes a priority rebaseline. That conflict must be resolved explicitly during review rather than silently interpreted.

---

## 22. Approval effect

If this plan is approved:

- Daily v1 becomes the primary product milestone.
- PC-4 remains parked.
- new feature work should be justified against Daily v1 gates;
- VPS router/multi-session work becomes the next implementation block;
- Writing Room moves from "future feature" to first usable milestone;
- Android fluidity becomes a release criterion rather than cosmetic polish;
- progress reporting should measure against M1-M8 instead of a vague total-project percentage.

Until approval, this file is a review draft only.
