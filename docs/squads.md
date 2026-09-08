# War Engine — Squads

Server-side, package `com.pigapl.warengine.squad` (+ the squad payloads under
`com.pigapl.warengine.network`, + `command/SquadCommand.java`). Added 2026-09-03, sitting between
the team and kit modules in the pick flow:

```
Team  ->  Squad  ->  Kit
```

## Why squads exist

Kit slot limits (`KitDefinition.limit`) used to cap how many players on a whole TEAM could hold one
kit. The user wanted that cap scoped to a smaller, player-formed group instead - a fireteam, not a
side. **Squads are that group**, and the kit limit was rescoped to count within a squad, not a team.

Unlike teams (vanilla scoreboard, admin-created, stable for a season) and kits (a config JSON
library), squads have **no life outside one event** - players form them live during staging. They
therefore live entirely in `WarState` (see its class javadoc), not in `config/`.

## Two limits, not one - don't confuse them

| | Squad roster limit | Kit slot limit |
|---|---|---|
| Set by | whoever creates the squad (`/squad create <name> <limit>`, or the Create Squad screen) | an admin, per kit (`/kit limit`) |
| Counts | every assigned member, **online or not** | **online** members only |
| Why | a roster is a persistent membership - a player doesn't lose their spot by disconnecting, same as team membership | a kit is a live "who's holding it right now" resource, same rule it always used, just rescoped from team to squad |
| Lives in | `WarState.SquadRecord.limit` | `KitDefinition.limit` |

## Data model

- `WarState.PlayerRecord.squadId` - one player's assigned squad, or `null`. Persists like `kitId`.
- `WarState.SquadRecord {id, team, name, limit}` - one squad's registration. Membership is derived
  (`WarState.squadMembers(id)`), not stored on the record.
- Squad ids are generated (`sq1`, `sq2`, ...), never chosen by the player - only the display `name`
  is free text.
- A squad with zero members is deleted automatically (`SquadService.leave`) - squads are clutter
  once empty, unlike a team, which an admin made on purpose and might reuse next event.
- A squad belongs to exactly one team. Switching teams vacates the old squad first (see
  `GameEvents.onTeamChangeTick`), same tick the old kit is dropped.

## Commands

```
/squad list                        squads on your own team, with roster counts
/squad create <name> <limit>       found a squad on your team and join it immediately
/squad join <id>                   join an existing squad on your team
/squad leave                       vacate your current squad
```

Validation and mutation live in `SquadService` (`create`/`join`/`leave`/`squadsFor`/`memberCount`) -
the same method backs both this command tree and the client-UI payload handlers in
`SquadNetworking`, so the two paths can never enforce different rules (same principle as
`KitService#assign`).

- Name: trimmed, non-blank, max `SquadService.NAME_MAX_LENGTH` (24) characters.
- Limit: `SquadService.MIN_LIMIT`-`MAX_LIMIT` (1-100). No "unlimited" option, unlike kits - a squad
  is a deliberately-sized fireteam.

## Network protocol (4 new payloads)

- S->C `ClientboundSquadListPayload(List<SquadEntry>)` - the caller's team's squads
  (`id, name, members, limit`). Sent at team assignment (replacing the old "send kit catalog at
  team assignment" trigger - the catalog now waits for a squad), and on any roster change.
- S->C `ClientboundSquadStatePayload(squadId)` - the player's own squad, `""` sentinel for none.
  Mirrors `ClientboundKitStatePayload` exactly (login + on change).
- C->S `ServerboundSelectSquadPayload(squadId)` - join an existing squad.
- C->S `ServerboundCreateSquadPayload(name, limit)` - found and join a new one.

`ClientboundKitCatalogPayload` now comes back **empty** until both team AND squad are set
(`KitNetworking.sendCatalogFor`) - the kit step is gated on squad the same way it used to be gated
on team alone.

## Client UI

- `SquadPickerScreen` - card grid (reuses `PickerLayout`/`SelectionCardWidget`, same visual language
  as team/kit), "Create Squad" and "Change Team" buttons. Auto-opens once a team is set but no squad
  is, waits for the (possibly empty) squad list before showing anything.
- `CreateSquadScreen` - two `EditBox`es (name, max players) + Create/Back. Client-side validates
  against `SquadService`'s own constants (read directly, not duplicated) before sending, purely so a
  bad value gets an inline message instead of a silent no-op - the server re-validates regardless.
- `ClientEvents`' state machine is now three steps: no team -> team picker; team, no squad -> squad
  picker (waits for the list); team+squad, no kit -> kit picker (waits for the catalog, as before).
- `KitPickerScreen`'s back button now reads "Change Squad" (one step back) instead of "Change Team"
  - `SquadPickerScreen` has its own "Change Team" button, one step further back.

## Refresh wiring

A squad change (join/create/leave, from the command OR the payload path - both funnel through
`SquadNetworking.refreshAfterSquadChange`) pushes:

1. The mover's own squad state.
2. The whole team's squad list (a roster count changed).
3. Kit catalogs for whoever shares the OLD squad (a slot just freed) and the NEW one (a slot was
   just taken) - not the whole team, since kit slot limits are squad-scoped now. See
   `KitNetworking.sendCatalogToSquad`, which sits alongside the still-team-scoped
   `sendCatalogToTeam` (used when a kit's access/name/limit/existence changes - that genuinely
   affects everyone on the team, regardless of squad).

A team change also vacates the old squad (`GameEvents.onTeamChangeTick`) and a logout refreshes only
squad-mates' kit catalogs (not the roster - membership persists through a disconnect, same as a
team).

## Kit budgets and per-squad reservations (2026-09-05)

Kit slot limits are no longer a flat `KitDefinition.limit` shared by every squad. Two levels now:

- **Team budget** - an admin sets an integer per `(team, kit)`: how many of that kit the WHOLE team
  may field. Stored in `teamkits.json` next to the kit access list (`"budgets": {"rpg": 5}`).
  Set it with `/kit budget <team> <kit> <count>` (0 clears) or the admin panel's **Kit Budgets**
  screen. `/kit budget` with no args lists every team's budgets with reserved/free counts.
- **Squad reservation** - when a squad is created, its founder claims some of each budgeted kit out
  of the team's budget (the squad-picker Create screen shows a `- N +  / remaining` stepper per
  budgeted kit). That reservation IS the squad's cap for that kit. A squad may take the team's
  entire remaining budget. `/squad reserve <squad> <kit> <count>` (op) edits it afterwards.

Resolution:

- A kit with **no** team budget behaves exactly as before: flat `KitDefinition.limit` per squad
  (unlimited if unset), shown in every squad's picker.
- A kit **with** a team budget is shown to a squad only if it reserved >= 1; the cap is the
  reservation; reserved 0 hides it from that squad (`/kit` reports `NOT_RESERVED`).

Reservations live on `WarState.SquadRecord.kitReservations` and are freed automatically when the
squad auto-deletes (last member leaves). The round-start scarce sweep
(`KitService.issueScarceWeapons`) caps per squad using the reservation, not the flat limit.

## Known gaps / next

- Editing a squad's reservations after creation is op-only (`/squad reserve` or - future - the
  planned per-team commander role); no player-facing edit screen. `EditSquadScreen` covers
  rename/roster-limit but not reservations yet.
- No kick - a member can only remove themselves (`/squad leave`); the admin panel can evict one.
- Not yet tested in-game (compiles + assembles clean in both projects as of 2026-09-05).
