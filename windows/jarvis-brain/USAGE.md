# JARVIS brain — usage

How to put the brain on a screen and how to drive it. The *why* (field, palette,
invariants) lives in `BRAIN.md`. Read that before changing the look.

There is one brain and two bodies. They share the `BrainDrive` numbers and the
three-angle field. **Do not give either body its own animation.** If a state
looks wrong, change the drive values, not the drawing code.

| Body | Where | Renders with |
| --- | --- | --- |
| Android | `app/src/main/java/com/jarvis/android/ui/components/JarvisBrain.kt` | Compose `Canvas` |
| Windows | `windows/jarvis-brain/index.html` | Three.js |

## Run it on Windows

Double-click `JarvisBrain.bat`. It serves this folder and opens
`http://127.0.0.1:8773/`. Needs Python 3 on `PATH`. Close the console to stop it.

Without Python, open `index.html` directly. Orbit (drag) and zoom (wheel) still
work; the import map pulls Three.js from `unpkg`, so it needs a network.

The buttons along the bottom switch mode. Use this viewer to judge a change
before porting it — it is the reference body.

## Put it on an Android screen

`JarvisBrain` takes the same arguments `JarvisOrb` already takes, so a call site
changes by name only.

```kotlin
JarvisBrain(
    size = 168.dp,
    activity = OrbActivity.THINKING,
)
```

| Argument | Default | Meaning |
| --- | --- | --- |
| `size` | `168.dp` | Square box. Below **72dp** it draws `JarvisOrb` instead — the lattice cannot be read that small, and chat avatars (22–30dp) must stay crisp. |
| `activity` | `IDLE` | One of `IDLE`, `LISTENING`, `THINKING`, `OFFLINE`. Maps to a `BrainDrive` row. |
| `intensity` | `0f` | `0f..1f`. Extra energy on top of the mode, e.g. streaming progress. |
| `contentDescription` | `null` | TalkBack label. Set it only when the brain is the *only* status on screen. Leave null when it is decorative. |

It already honors `LocalReducedMotion`: the field freezes on the pose of the
current mode. Do not add a second reduced-motion check around it.

Where it stands today: welcome, pairing, lock, and the empty states of
conversations, approvals and tasks. The small orbs in chat rows and task rows
stay `JarvisOrb` on purpose.

## Drive it from real state

The renderer eases toward a target. It never snaps and it never decides. You
only choose the target.

On Android the target is `OrbActivity`. Pick it from state you already have:

| What is happening | Activity |
| --- | --- |
| App locked, or the gateway is unreachable | `OFFLINE` |
| User is speaking, or the mic is open | `LISTENING` |
| A request is in flight, tokens are streaming | `THINKING` |
| Nothing in flight | `IDLE` |

```kotlin
val activity = when {
    !gateway.online -> OrbActivity.OFFLINE
    session.isStreaming -> OrbActivity.THINKING
    else -> OrbActivity.IDLE
}
JarvisBrain(size = 168.dp, activity = activity, intensity = session.progress)
```

There is no `SPEAKING` or `WAITING` in `OrbActivity` yet. On Android those fall
under `THINKING` and `IDLE`. Add them to the enum and to `OrbActivity.drive()`
in `JarvisBrain.kt` when the app can actually tell them apart.

On Windows the same rows live in the `DRIVE` table in `index.html`, which also
has `SPEAKING` and `WAITING`. To drive it from the PC backend instead of the
buttons, set `mode` (or write `drive` directly) from the event stream:

| Signal | Channel |
| --- | --- |
| Tokens per second | `energy` |
| A tool call in flight | lower `coherence` |
| Silence, waiting on the user | lower `tempo` |
| Audio output level, per frame | `warmth` |

## Add a new mood

Add a row, do not add a clip. A mood is five numbers:

| Channel | Range | Effect |
| --- | --- | --- |
| `energy` | 0..1 | Core brightness, membrane amplitude, how many neurons fire |
| `focus` | 0..1 | Pulls the lattice inward, straightens the rings |
| `tempo` | 0.1..2 | Speed of the field. Changes time, not shape |
| `coherence` | 0..1 | High = ordered. Low = rings wobble, violet shows on the hottest nodes |
| `warmth` | 0..1 | Shifts toward amber. Keep it low except while speaking |

Keep both tables in step: `OrbActivity.drive()` in `JarvisBrain.kt` and `DRIVE`
in `index.html`. If they diverge, the phone and the PC disagree about what
"thinking" looks like.

## Do not

- Do not keyframe a state as a timed clip. The loop point always shows.
- Do not spin the whole thing on one axis at constant speed. That is the old orb.
- Do not add a second hue. The palette is one cyan family; violet is a thin
  accent on hot nodes, amber is speech only. Three colours at once means it is
  wrong.
- Do not lower the 72dp fallback. The lattice turns to noise under it.
- On Android, do not move work out of the `Canvas`. The frame clock only
  invalidates the draw, so the screen around the brain never recomposes.
