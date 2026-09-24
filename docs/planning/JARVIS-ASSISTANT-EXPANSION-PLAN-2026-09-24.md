# JARVIS Assistant Expansion Plan

Date: 2026-09-24
Status: Planning only — no runtime behavior changed by this document.

## Goal

Evolve JARVIS from a capable AI chat/client into a general-purpose personal agent for both power users and ordinary day-to-day use, while preserving the existing Android/PC/VPS/Hermes architecture and approval/security model.

## Current JARVIS foundation to preserve

The Android app already includes or is actively building around:

- Persistent conversations and reconnection/recovery
- Projects
- Approvals and tasks
- Biometric gates for sensitive actions
- Notifications
- Floating bubble / overlay
- Screen vision
- Writing Room / Wiki / lore workflows
- Provider-usage visibility
- Multi-session support
- PC/VPS integration surfaces

The expansion should reuse these surfaces rather than create parallel systems.

---

## Phase A — Agent / power-user expansion

### A1. Automation Center

Natural-language automations built on Hermes cron, skills, browser/tools, events and approvals.

Examples:

- "Every morning summarize what needs my attention."
- "If a GitHub workflow fails, inspect it and notify me."
- "Watch this page and tell me when the value changes."
- "Run this routine every Friday, but ask before any destructive step."

UI idea: Automations screen with Active, Paused, History, Last run, Next run, and Approval required badges.

### A2. Android Actions

Allow JARVIS to perform safe native device actions through an Android action executor, permission model and owner approval policy.

Candidate actions:

- Open apps / deep links
- Create alarms/timers
- Control media
- Read/action selected notifications
- Launch navigation
- Share content
- Change approved device settings where Android APIs permit it

### A3. JARVIS Live

Continuous conversational mode using voice plus camera/screen context.

Target experience:

- Hands-free conversation
- Camera understanding
- Screen sharing / visual guidance
- Interruptible responses
- Optional wake phrase
- Handoff between Android and PC

### A4. Skills & Connections Center

Expose Hermes skills and external connections from Android.

Capabilities:

- Browse installed skills
- Enable/disable skills
- Show permissions/tools each skill can use
- Install approved skills
- Create a skill from a repeated workflow
- Manage external connectors/MCP integrations
- Health/status for each connection

### A5. Mission Control

Visualize delegated/sub-agent work from Hermes.

Show:

- Parent task
- Child agents / roles
- Current step
- Progress / state
- Tool or model in use when appropriate
- Cost/usage where available
- Pause/cancel/approve controls
- Final merged result

---

## Phase B — Everyday / casual assistant expansion

The goal of this phase is to make JARVIS useful to someone who does not code and does not care about AI models.

### B1. Daily Briefing

A configurable morning or on-demand briefing combining only the sources the user chooses.

Possible cards:

- Calendar / appointments
- Weather
- Important messages
- Packages / deliveries
- Bills or due reminders
- Tasks
- Family reminders
- Commute / leave-by reminders
- News topics selected by the user

The briefing should be conversational: "What matters today?" rather than a dashboard the user must manage.

### B2. Universal Reminder + Follow-up Assistant

More capable than a normal reminder app.

Examples:

- "Remind me when I get home to take the boxes out of the car."
- "If I haven't replied to this message by tomorrow afternoon, remind me."
- "Remind me three days before this subscription renews."
- "Keep reminding me about this every evening until I mark it done."

Use approvals whenever JARVIS would message another person or take an external action.

### B3. Family Assistant

Optional per-user profiles and permissions for household use.

Examples:

- Shared grocery list
- School/event reminders
- Chores
- Family calendar summary
- "Who needs to be where tomorrow?"
- Shared household notes
- Different permissions for owner/adult/child profiles

### B4. Shopping & Errand Assistant

Conversational shopping without forcing the user to manually compare ten tabs.

Examples:

- Build grocery lists from natural language
- Organize by store/category
- Compare products/prices online
- Track desired-price thresholds
- Remember preferred brands/sizes when explicitly allowed
- Prepare an order/cart but require approval before purchase
- Create errand routes and checklists

### B5. Message Helper

Casual communication assistance across connected services.

Examples:

- "Summarize what I missed."
- "Which messages actually need an answer?"
- Draft replies in the user's style
- Translate incoming/outgoing messages
- Remind about unanswered important conversations

Sending should remain approval-gated unless a narrowly scoped automation is explicitly authorized.

### B6. Personal Organizer / Life Inbox

One place to dump unstructured thoughts by voice, text, photo or share-sheet.

Examples:

- "Remember this restaurant."
- Photo of a receipt → save and categorize
- Screenshot → extract action items
- Voice note → turn into tasks/reminders
- Link → save with a short summary and tags

JARVIS decides whether the item belongs in memory, tasks, a project, shopping, calendar, or a saved collection, and asks when uncertain.

### B7. Home Assistant / Smart Home

Use Hermes' Home Assistant toolset as the house-control layer.

Examples:

- "I'm going to bed" routine
- "Did I leave anything on?"
- Temperature/light/fan control
- Sensor alerts
- Presence-based routines
- Household status summaries

Sensitive devices such as locks, garage doors or security systems should require stronger policy/approval controls.

### B8. Travel / Outing Assistant

Lightweight planning focused on execution, not research reports.

Examples:

- "Plan Saturday with the kids under $100."
- Keep reservation/address/ticket details together
- Build a leave-by plan
- Navigation handoff
- Weather-aware packing checklist
- Translate signs/menus via camera
- Track itinerary changes through connected messages/calendar

### B9. Food / Meal Assistant

Use preferences and household constraints only when the user chooses to store them.

Examples:

- "What can I cook with what I have?"
- Weekly meal ideas
- Grocery list generation
- Leftover reminders
- Recipe scaling/substitutions
- Hands-free cooking mode with timers

### B10. Personal Routines

Reusable named routines implemented as Hermes skills/automation blueprints.

Examples:

- Morning
- Leaving home
- Arriving home
- Bedtime
- Grocery day
- Travel day
- Movie night
- School morning

A routine can combine reminders, Home Assistant actions, browser/connector lookups, notifications and voice output.

### B11. "Handle This" Share Action

Android share-sheet target: Share text, a URL, screenshot, document or image to JARVIS and choose or infer an action.

Examples:

- Summarize
- Save
- Remind me
- Add to shopping
- Translate
- Explain
- Compare
- Create task
- Send to a project

This should become one of the fastest everyday entry points into JARVIS.

### B12. Contextual Suggestions, not intrusive notifications

JARVIS can surface small suggestions when confidence is high, e.g.:

- "You usually leave in 20 minutes and traffic is heavier today."
- "This bill is due tomorrow."
- "You mentioned buying this when it dropped below your target price."

Rules:

- Easy global/off-per-category controls
- Explain why a suggestion appeared
- No sensitive inference
- No action without the required approval tier

---

## Hermes capabilities to exploit

Map these to UI rather than reimplementing them:

- Persistent memory and session recall
- Skills and self-created/reusable procedures
- Cron / scheduled jobs
- Browser automation
- Web search/extraction
- Vision
- Voice/TTS
- Delegation/sub-agents
- Computer use on PC
- Home Assistant tools
- MCP / managed connections to external services
- Messaging gateway delivery

---

## Suggested implementation order

### Near-term foundation

1. Automation Center
2. Skills & Connections Center
3. Universal Reminder / Follow-up system
4. "Handle This" Android share action
5. Daily Briefing

### Next

6. JARVIS Live voice
7. Android Actions
8. Life Inbox
9. Family profiles / shared household surfaces
10. Home Assistant integration UI

### Later / advanced

11. Mission Control / delegated agents
12. Cross-app visual execution on Android
13. Rich travel/shopping execution flows
14. Proactive contextual suggestions

## Design principle

For ordinary users, hide models, providers and agent mechanics by default. The casual experience should be phrased in outcomes — "remind me", "organize this", "find this", "handle this", "what matters today?" — while owner/developer mode can expose routing, models, skills, traces and provider usage.
