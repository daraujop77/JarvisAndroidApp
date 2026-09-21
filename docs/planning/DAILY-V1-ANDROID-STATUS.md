# Daily v1 — Android status

Where the Android client stands against
`docs/planning/JARVIS-DAILY-V1-FIRST-USABLE-OBJECTIVE.md` (mirror of the
control-plane plan, commit `b2b02ec`). That plan is still a draft awaiting owner
approval. This file only records what the client can already do.

The control-plane plan is the source of truth for scope. This file is the
source of truth for what landed in `JarvisAndroidApp`.

## Done in the client

### The brain (M7, pulled forward)

`ui/components/JarvisBrain.kt` is the holographic brain, drawn in a Compose
`Canvas`. It is the hero mark on welcome, pairing, lock, and the empty states.
Below 72dp it falls back to `JarvisOrb`, so chat avatars stay crisp.

The Windows reference body is `windows/jarvis-brain/`. How to call and drive it:
`windows/jarvis-brain/USAGE.md`. The field law: `windows/jarvis-brain/BRAIN.md`.

The brain shows state. It does not route. That matches the plan's rule that the
client holds no routing authority.

### One front door (M1, client half)

`PairingScreen` no longer defaults to `http://desktop-l59hjk4/`. That hard-coded
PC address is what sent a fresh install straight to `CONNECTING -> DISCONNECTED`.

The field now starts empty and is filled from `Settings.lastControlPlaneUrl`,
which is written only after a login actually succeeds. The phone remembers the
door that worked instead of guessing one.

The label says "Control plane URL". The phone still does not decide between
cloud and PC — it names one endpoint and the control plane routes. That is as
far as the client can go. The VPS router itself lives in `daraujop77/jarvis`,
not here.

### Distinct failure reasons (M1 gate, M7)

`SessionState.connectionDetail` now keeps the `reason` the transport already
sends on `connection.state`. The connection banner reads two markers:

| Reason contains | Banner shows |
| --- | --- |
| `pc_worker_offline` | PC offline — cloud still available |
| `control_plane_unavailable` | Control plane unreachable |

Anything else stays the plain status word. The client never invents a cause.

Checked against `daraujop77/jarvis` branch `feature/vps-authoritative-router-m1-1`:
the router already raises `pc_worker_offline` (HTTP 502) when the home PC is
down, from `runtime/linux/jarvis_vps_app.py`. So that half of the banner is live
as soon as the phone points at the VPS. `control_plane_unavailable` is not
emitted by anything yet — the control plane cannot report its own absence — so
that hint stays dormant until a client-side timeout is wired to it.

## The missing piece: the VPS address

The router is real and it serves `/api/app/login`, `/api/app/chat`,
`/api/app/chat/stream` and `/api/app/chat/cancel` — the same surface this app
already speaks. It listens on `127.0.0.1` and is published only through
Tailscale Serve, so it has no public address.

No branch names the VPS node. `100.76.120.40` in `docs/jarvis-admin.md` is the
owner's phone, not the server. This machine is not on the tailnet, so the name
cannot be looked up from here.

When the owner runs `tailscale status` on any tailnet device, the VPS node is
the one serving JARVIS. Its MagicDNS name (`<node>.<tailnet>.ts.net`) is the
value for the Control plane URL field. The app remembers it after the first
successful login and never asks again. Do not hard-code it: a tailnet name is
not a stable contract, and putting it in the repo would publish the front door.

## Not done — and where it has to be done

These are the plan's next gates. None of them can be finished inside this repo
alone, because the router is the control plane's job.

| Gate | Needs | Repo |
| --- | --- | --- |
| M1 Phone → VPS → cloud, streaming, same endpoint | the VPS router and its URL | `daraujop77/jarvis` |
| M1 Phone → VPS → PC local model | PC worker advertising capabilities | `daraujop77/jarvis` |
| M1 no provider authority in Android | already true — keep it true | this repo |
| M2 three isolated phone conversations + one PC task | server-side session isolation | both |
| M3 projects as server objects | workspace service | `daraujop77/jarvis` |

## Open before M3

The plan's section 20 leaves these unanswered, and M3/M4 cannot start without
them. They are owner decisions, not code:

- Where authoritative canon lives: Git, the VPS database, or a defined hybrid.
- Whether SQLite on the VPS is acceptable for the first story state.
- Which embedding model builds the first RAG index.
- The VPS hostname. The client now remembers it, but somebody has to know it.

## Rule for the next change

Do not add routing logic to Android. If a change needs the phone to know whether
a request runs on the cloud or the PC, it belongs in the control plane and the
phone should only display the result.
