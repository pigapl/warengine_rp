# War Engine

Event-running tools for weekly modded combat events on Minecraft **1.21.1** / **NeoForge 21.1.233**.

Two jars are built from this repo:

| Jar | Mod id | Where it goes |
|---|---|---|
| `warengine_pigapl-0.1.0.jar` | `warengine_pigapl` | Server **and** every player's client |
| `warengine_pigapl_client-0.1.0.jar` | `warengine_pigapl_client` | Player clients only |

The base mod holds all gameplay logic and is fully server-authoritative. The client addon is the
UI and HUD layer — pickers, the admin panel, and the capture readout. Both are required for the
intended experience: without the addon there is no war readout on screen.

## What it does

**Teams → Squads → Kits.** Players pick a team, form or join a squad inside it, then draw a kit.
Each step is a card-grid screen that opens itself when the player owes it.

- **Teams** are vanilla scoreboard teams, so colour, friendly-fire and nametags come for free.
  Which kits a team may use is saved per server in `teamkits.json`, so it survives a map swap.
- **Squads** are player-formed subgroups that live for one event only. Kit limits are per squad.
- **Kits** are captured from an admin's own inventory (`/kit save <id>`) and stored as JSON with
  full data-component state, so TACZ guns and Create contraptions round-trip intact. On respawn a
  kit is *reconciled* — you keep what you're carrying and only what's missing is topped up.
- **Kit budgets** cap how many of a kit a whole team may field. Squads claim reservations out of
  that budget when they are created.
- **Scarce weapons** (RPG, sniper, LMG) are issued exactly once, at the whistle. Kit picks and
  respawns both withhold them, so a dropped launcher is gone for the round.

**Capture points.** `/war start [minutes]` opens a land grab over N named points. Standing on a
point fills its capture bar; once it flips, **ownership is sticky** — it pays its owner every second
whether or not anyone is still standing there. Presence *takes* a point, it does not hold it. Any
enemy inside freezes the bar and the point pays nobody. First team to `round.ticketCap` wins.

Points are drawn two ways on purpose: the HUD always shows who owns what, while the in-world
markers are depth-tested particles that thin out with distance — so a point behind a hill is
genuinely invisible and you have to go look.

**Admin panel.** `/warstate admin` opens a live dashboard: start/stop the war, tickets, rosters,
per-point status and teleports, the capture history log, the kit library, budgets, scarce weapons
and team bases. Every payload it sends re-checks op status server-side.

**Team bases.** One saved position per team driving respawn, "TP all to bases", and the radius
inside which a kit may be picked. A team with no base is unrestricted — that is the off switch.

## Docs

| File | Covers |
|---|---|
| [`docs/kit-system.md`](docs/kit-system.md) | Kits, `/kit`, team mapping, scarce weapons, reconcile |
| [`docs/squads.md`](docs/squads.md) | Squads, `/squad`, roster vs kit limits |
| [`docs/war-rounds.md`](docs/war-rounds.md) | Rounds, `/war`, capture points, tickets |
| [`docs/bases.md`](docs/bases.md) | Team bases and `/warstate base` |
| [`docs/admin-panel.md`](docs/admin-panel.md) | The admin dashboard and its payloads |

## Building

```
./gradlew build
```

Produces `build/libs/warengine_pigapl-0.1.0.jar` and
`warengine_pigapl_client/build/libs/warengine_pigapl_client-0.1.0.jar`. Those two jars are the
whole deliverable — drop them into whatever modpack you run.

## Development

Two clients are needed to test anything involving teams:

```
./gradlew :warengine_pigapl_client:runClient
./gradlew :warengine_pigapl_client:runClient2
```

Use the **client subproject's** run tasks, not the root `runClient` — the root task loads only the
base mod and the addon silently will not load.

Server config lives at `run/config/warengine_pigapl-server.toml`; kits, team mappings and the
scarce list at `run/config/warengine_pigapl/`. None of `run/` is tracked here.

## Licence

All Rights Reserved. Minecraft mapping names are covered by
[Mojang's mapping licence](https://github.com/NeoForged/NeoForm/blob/main/Mojang.md).
