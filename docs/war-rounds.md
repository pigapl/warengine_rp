# War Engine — Round system

Server-side, package `com.pigapl.warengine.round`. A small timed-round layer on top of the existing
kit/team modules. All state lives in `WarState` (the same overworld `SavedData` the kit module uses),
so it persists and there is still one source of truth.

## What it does

| Piece | Behaviour |
|---|---|
| Timed war | `/war start [minutes]` runs a war for a fixed length (default `round.defaultDurationMinutes` = 10). It auto-ends when the clock runs out. |
| Capture points | **N points, redesigned 2026-09-09** (was a single zone). `/war point add <id>` drops one at your feet. Each has an owner, which is sticky. |
| Capture bar | Standing on a point you do not own fills its bar. Progress per second = the number of your players on it, capped at `round.captureMaxPlayers` (6). At `round.captureSeconds` (30) the point flips to you. Two or more teams inside = **contested**: the bar freezes and the point pays nobody. |
| Tickets | **Earned, not spent.** Every team starts a war at 0. Each second, every point a team OWNS and that is not contested pays `round.zoneTicketsPerSecond` (default 1). Owning three points is 3/s. First team to `round.ticketCap` wins immediately. Kills never affect tickets. |
| Result | On end (ticket cap, time up, or `/war end`) the team with the most tickets wins; equal = draw. Announced as a title + chat line to everyone. |
| HUD | The client addon draws a pill per point across the top of the screen in its owner's team colour with the capture bar under it, your team's ticket bar to the left of them and everyone else's to the right. The addon is required - without it there is no war readout on screen. |
| Scarce weapons | `/war start` is the whistle: it calls `KitService.issueScarceWeapons` once, the only moment RPG/sniper/LMG-type items enter the world. No-op if the scarce list is empty. See `docs/kit-system.md`. |

The round end time is stored as wall-clock epoch millis, not a server tick, so a mid-war server
restart doesn't break the countdown. Point ownership and a half-filled capture bar persist too.

## The capture mechanic, second by second

For each point, once a second:

1. Two or more teams inside → **contested**. Bar frozen, point pays nobody.
2. Nobody inside → the bar drains by `round.captureDecayPerSecond` (2). The **owner still gets paid**.
3. Only the owner inside → the bar drains (they are pushing an attacker's progress back), owner paid.
4. Only one other team inside → their bar fills by `min(players, captureMaxPlayers)`. If a different
   team was mid-capture, the bar restarts from 0 rather than being inherited. At `captureSeconds` the
   point flips, is announced in chat, and is written to the admin panel's history log.

At the defaults that is: 1 attacker 30s, 2 → 15s, 3 → 10s, 4 → 8s, 5 → 6s, 6 or more → 5s.

**Ownership is sticky, and that is the whole design.** Presence *takes* a point; it does not hold it.
Walk away from a point you captured and it keeps paying you until somebody flips it back.

`/war start` resets every point to neutral, so each round opens as a land grab.

## Where you can see a point

Deliberately two separate layers:

- **On the HUD, always.** Every point, its owner and its capture bar, whether or not you are near it.
- **In the world, only where you could really see it.** Markers are ordinary depth-tested particles,
  so a point behind a hill shows nothing and nothing is ever drawn through a wall. There is no marker
  floating in the sky.

The world marker **thins out with distance rather than cutting off**, so a point stays findable from
across the map without cluttering it:

| Distance | What you see |
|---|---|
| ≤ 48 blocks | full ring + centre pillar |
| ≤ 144 blocks | every second ring position + pillar |
| ≤ 512 blocks | pillar only — one small beacon |
| beyond 512 | nothing (vanilla's own particle ceiling) |

The ring is drawn in the owner's team colour (grey when neutral), and while a capture is in progress
the filled fraction of the circle switches to the attacking team's colour — the capture bar is
literally on the ground.

**The ring hugs the terrain.** Each ring position is dropped onto the first solid block below it,
searched within a short window around the point's own Y (4 up, 12 down), so the circle follows a
slope instead of floating as a flat disc. A short window is used rather than a heightmap on purpose:
a heightmap would put the ring on a roof or a bridge above the point. A position with nothing solid
in that window (a cliff edge) falls back to the point's own height rather than plunging.

Note: clients on the "Minimal" particle setting see fewer particles. On a hand-built map the robust
answer is to build a physical structure at each point.

## The HUD, and its settings

```
        156 ██████░░░░   [A] [B] [C]   ░░░░██████ 98
             you                            them
```

Your team's ticket bar sits left of the points, every other team's to the right. Both fill from the
inner edge outwards, so the fill always starts next to the points and you read its length away from
the centre. Each bar carries a small number. A point pill is filled in its owner's colour (grey when
neutral), outlined yellow while contested, with its capture bar underneath.

**Press `J` for HUD settings** (rebindable, under Controls → War Engine). The same screen is at
Mods → War Engine Client → Config. It is built on vanilla's own options-screen framework, so it
looks and scrolls like Video Settings, and the real HUD is redrawn on top of it at full brightness
while you change things — sizing it is a live preview, not guesswork.

| Setting | Default | |
|---|---|---|
| HUD Size | 100% | 50–250%. The whole block scales as one piece. |
| Distance From Top | 4px | 0–200. |
| Capture Points | on | The row of pills. |
| Capture Bars | on | The progress bar under each pill. |
| Ticket Bars | on | The two filling lines. |
| Ticket Numbers | on | The small count at the end of each bar. |
| Income Rate | **off** | `+3/s` per team. Off by default — the bars already show who is pulling ahead. |
| Projected Time To Win | **off** | Time to the cap at the current rate. |

Settings are per player in `config/warengine_pigapl_client-client.toml` (CLIENT config — it never
reaches the server) and save the moment you change them, so there is no Save button.

## Commands

```
/war start [minutes]          (op)   start a war (1–240 min; default from config)
/war end                      (op)   end it now and announce the result
/war status                          time left, tickets, and every point
/war point add <id> [radius]  (op)   create/move a point at your feet (id ≤ 8 chars)
/war point remove <id>        (op)   delete one
/war point clear              (op)   delete all
/war point list                      ids, positions, owners, capture progress
/war point tp <id>            (op)   teleport to one
```

Everything stays under the one `war` root: Brigadier **merges** a duplicate root literal rather than
rejecting it, silently overwriting same-named children.

The admin panel (`/warstate admin`) shows the same points live — owner, capture progress, who is
standing on each — with a per-point `TP` button, and flashes when any point changes hands.

## Config (`config/warengine_pigapl-server.toml`)

```
round.defaultDurationMinutes  = 10     # /war start with no argument
round.ticketCap               = 500    # first team to reach this many wins
round.zoneTicketsPerSecond    = 1      # gained per second PER OWNED POINT
round.zoneRadius              = 5.0    # default radius for /war point add; also the capture radius
round.captureSeconds          = 30     # progress needed to flip a point (= a 30s solo capture)
round.captureMaxPlayers       = 6      # attacker-count ceiling for capture speed
round.captureDecayPerSecond   = 2      # progress lost per second when nobody is pushing the bar
```

**`ticketCap` is point-seconds, not seconds.** With 3 points at 1/s, owning all three earns 3/s, so
500 is about 2:47 of total dominance, 4:10 on two points, 8:20 on a one-point lead. It went from 60
to 500 in the redesign because scoring is now up to 3× and runs continuously instead of only while
somebody stands in the zone — the old 60 would have ended a round in twenty seconds. It is editable
live from the admin panel, so tune it once you have seen real round lengths.

## Notes / limits

- Tickets are keyed to whichever teams exist at `/war start` — all start at 0. A team created mid-war
  is not ticketed, cannot score, and does not count as an occupant for contesting until the next start.
- "Contested" is strict: one enemy stepping onto a point stops the owner's income for that second,
  even if outnumbered, and freezes the bar. No partial or majority credit.
- `/war start` warns (but still runs) if no teams or no points exist.
- No points set = nobody can ever score; the war just runs out the clock.
- A world saved before 2026-09-09 with the old single `zone` is migrated on load: it becomes point `A`
  at the same coordinates and radius.
