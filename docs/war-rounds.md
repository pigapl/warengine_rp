# War Engine — Round system

Server-side, package `com.pigapl.warengine.round`. A small timed-round layer on top of the existing
kit/team modules. All state lives in `WarState` (the same overworld `SavedData` the kit module uses),
so it persists and there is still one source of truth.

## What it does

| Piece | Behaviour |
|---|---|
| Timed war | `/war start [minutes]` runs a war for a fixed length (default `round.defaultDurationMinutes` = 10). It auto-ends when the clock runs out. |
| Tickets | **Earned, not spent (redesigned 2026-09-03).** Every existing scoreboard team starts a war at 0. Once a second, whichever team holds the marker zone UNCONTESTED — at least one of its players inside, no enemy player inside — gains `round.zoneTicketsPerSecond` (default 1). An empty or contested zone (both teams present) awards nobody that second. First team to reach `round.ticketCap` (default 60) wins immediately. Kills no longer affect tickets at all. |
| Result | On end (ticket cap reached, time up, or `/war end`) the team with the most tickets wins; equal = draw. Announced as a title + chat line to everyone. |
| Marker zone | `/war setzone [radius]` drops a visible circle of END_ROD particles (default `round.zoneRadius` = 5 blocks) at your feet, plus a short pillar at the centre. Drawn every 8 ticks whenever a zone is set — war or not. `/war clearzone` removes it. This is also the capture radius used for scoring. |
| HUD | While a war runs, every player sees `WAR  m:ss   red 12  blue 5   (first to 60)` on the action bar once a second. |
| Scarce weapons | `/war start` is the whistle: it calls `KitService.issueScarceWeapons` once, the only moment RPG/sniper/LMG-type items enter the world. No-op if the scarce list is empty. See `docs/kit-system.md`. |

The round end time is stored as wall-clock epoch millis, not a server tick, so a mid-war server
restart doesn't break the countdown.

## Commands

```
/war start [minutes]   (op)   start a war (1–240 min; default from config)
/war end               (op)   end it now and announce the result
/war status                   time left + each team's tickets + zone location
/war setzone [radius]   (op)   put the marker circle at your position (1–128; default from config)
/war clearzone         (op)   remove the marker circle
```

## Config (`config/warengine_pigapl-server.toml`)

```
round.defaultDurationMinutes = 10     # /war start with no argument
round.ticketCap               = 60    # first team to reach this many tickets wins
round.zoneTicketsPerSecond    = 1     # gained per second by whoever holds the zone uncontested
round.zoneRadius              = 5.0   # /war setzone with no argument; also the capture radius
```

## Notes / limits

- Tickets are keyed to whichever teams exist at `/war start` — all start at 0. A team created
  mid-war is not ticketed and cannot score until the next `/war start`.
- "Uncontested" is strict: one enemy player stepping into the zone stops the holding team's gain
  for that second, even if outnumbered. No partial/majority credit.
- `/war start` warns (but still runs) if no scoreboard teams exist yet.
- No zone set = nobody can ever score; the war just runs out the clock (draw unless tickets differ,
  which they can't without a zone) or waits for `/war end`.
