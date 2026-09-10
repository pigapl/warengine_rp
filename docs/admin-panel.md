# War Engine — Admin panel

Added 2026-09-03, same day as squads. Server-side entry point `command/WarStateCommand.java`
(`/warstate admin`), payloads in `network/` (prefixed `Admin*`/`ClientboundAdminSnapshotPayload`/
`ServerboundAdmin*`), client screen `warengine_pigapl_client/.../gui/AdminScreen.java`.

## What it's for

Running an event entirely through `/war`/`/kit`/`/squad` chat commands got tedious once there was
enough state to track (tickets, rosters, zone occupancy, capture history). This is a single-screen
dashboard: start/stop the war, and a live read-only view of everything an admin would otherwise have
to piece together from several `/war status`-style commands.

## Opening it

`/warstate admin` (op only). Sends `ClientboundOpenAdminScreenPayload` to the caller, which the
client's `ClientEvents` tick loop turns into `mc.setScreen(new AdminScreen())`.

**Deliberate exception to the "server never tells the client to open a screen" rule** the team/
squad/kit pickers follow: those are auto-driven, opening themselves whenever their owed state says
to. The admin screen has no such derivable "you owe this" state - it is an explicit imperative
action, closer to what a normal command already does than to a picker. `ClientAdminCache`'s
one-shot `openRequested` flag (mirrors the kit/squad caches' shape) carries the request from the
base-mod payload handler to the client addon's tick loop, which is the only place addon code runs.

## What the screen shows

- **Status bar**: `WAR RUNNING - 4:32 left - first to 500 tickets` / `NO WAR RUNNING`.
- **Start War (5m/10m/20m presets) / Stop War** buttons - send `ServerboundAdminStartWarPayload`/
  `ServerboundAdminEndWarPayload`, which call the exact same `RoundService.start`/`end` the `/war`
  command tree calls (no parallel logic to drift).
- **Teams panel**: per team - colour swatch (from the scoreboard, same as the team picker), ticket
  count, online player count, and the online player names (wrapped).
- **Capture Points panel** (rewritten 2026-09-09 for N points): one row per point - id, owner,
  coordinates and radius, plus what is happening on it RIGHT NOW (`CONTESTED - <names>`,
  `<team> capturing 12/30 (<names>)`, `held - <names>`, or `nobody inside`) and a per-point `TP`
  button. Occupancy is computed by the exact same rule `RoundService.tickCapturePoints` scores by
  (`RoundService.occupantsByTeam`, public for this reason) - never a second implementation that could
  quietly disagree with what actually scores. Row Y positions come from one shared
  `forEachVisiblePointRow` visitor used by both `init` (buttons) and `render` (text).
- **History panel** (scroll to browse, newest first): every time a point actually changed hands -
  `[14:03:11] A taken by RED (Steve, Alex)`, `[14:05:40] B went neutral`. This is the "who had it,
  what person" log the user asked for.

## Live data, not push

Unlike the kit/squad catalogs (pushed on every relevant change), the admin snapshot is **polled**:
the screen sends `ServerboundRequestAdminSnapshotPayload` once on open and then every 20 ticks
(~1s) while it stays open; the server has no idea whether the screen is open otherwise, so nothing
is pushed to an admin who isn't looking. Same "no clean change event, so poll" reasoning as
`TeamPickerScreen`'s scoreboard signature check.

## The capture history log (new WarState data)

`WarState` holds a bounded `captureHistory` list (last 50 flips, oldest evicted first), persisted
with the world. `RoundService.tickCapturePoints` recomputes every point's state each second (as it
already does for scoring) but `WarState.setPointOwner` appends a `WarState.CaptureLogEntry` only on
an actual flip - not one entry per second. An entry records the epoch millis, which point, the new
owner (`null` = went neutral) and the player names present at that moment.

Since 2026-09-09 each entry also carries the point id. Entries written before that load with an
empty id and render without one, rather than failing to load.

## Security note: payload handlers self-check op status

A Brigadier command's `.requires(src -> src.hasPermission(2))` is enforced by the dispatcher before
the command body ever runs. A raw network payload handler gets **no such automatic gate** - anyone
connected can send any registered payload. `/warstate admin` being op-gated only stops a non-op from
receiving the "open the screen" nudge; it does not stop a modified client from sending
`ServerboundAdminStartWarPayload` directly. Every inbound handler in `AdminNetworking` therefore
re-checks `player.createCommandSourceStack().hasPermission(2)` itself
(`AdminNetworking.opPlayerOrNull`) before doing anything.

## Second screen: Teams & Squads

Added same day, right after the event screen - the user asked for it separately ("all the things
for the screens not just 1"). Reached via a "Teams & Squads" button on `AdminScreen`;
`AdminTeamsScreen` has its own "Back to Event" button back the other way. Same polling design.

- **Teams panel**: every team server-wide (not just the caller's), color swatch, display name, and
  its ONLINE members - matches the event screen's team panel convention (online only; a scoreboard
  can hold offline members with no clean way to enumerate them by name without a profile-cache
  round trip, and an admin managing a live event doesn't need offline scoreboard rows).
- **Squads panel** (scrollable): every squad server-wide, one header row (name, team, roster
  `members/limit`) followed by one row per member - name, **assigned kit** (raw kit id, `(no kit)`
  if none), an `(offline)` tag if not connected, and a **Kick** button. Unlike the teams panel, squad
  rosters DO include offline members and resolve their names via the server's profile cache
  (falling back to a truncated id if unresolvable) - a squad roster is a persistent membership (see
  `SquadService`), so an admin pruning stale/AFK members needs to see them to kick them.
- **Kick**: `ServerboundAdminKickSquadMemberPayload(UUID)`. Works on an offline target - squad
  membership tracks by id, not by presence (`SquadService.kick`, refactored out of the old
  `leave(ServerPlayer)` so self-leave and admin-kick share one implementation). Refreshes the
  target's own squad state (if online), the whole team's squad list, and kit catalogs for whoever's
  left in the vacated squad - same wiring `SquadNetworking` already uses elsewhere.

**Layout note:** the squads list mixes read-only text (drawn in `render`) with real interactive Kick
buttons (created in `init`) at the SAME row positions - one shared method,
`forEachVisibleSquadRow`, computes row Y-coordinates for both, so the two can never drift apart the
way two independent layout calculations could. Same "one method, two callers" principle used
throughout this mod for anything that must stay in sync (e.g. `KitService.assign`).

## Full admin suite (added same day, third pass)

After the first two screens, the user asked for everything else on this list to also be built:
team create/delete/recolor from the panel, squad rename/re-limit, per-player actions, a kit-library
screen, and a scarce-weapons screen. All op-gated, all payload handlers self-checking permission the
same way as everything above.

**Teams & Squads screen, upgraded:**
- Team header row: a "Color" button cycles through the 16 vanilla colors
  (`ServerboundAdminUpsertTeamPayload` - the same call `TeamKits#addTeam` already makes for create,
  since it both creates-or-adopts a team AND (re)sets color in one step, so this payload does double
  duty), and a "Del" button (`TeamKits#removeTeam`).
- Top-of-screen controls: a text field + "Create Team" button, and a "Restore Teams" button
  (`TeamKits#restore` - the fresh-world setup command, now one click).
- Per-player row (teams panel, online only): "TP" (teleport the admin to them) and "Move" (opens
  `AdminPickScreen` listing every team; picking one calls `TeamService#assign` - the existing
  `GameEvents.onTeamChangeTick` poll handles the kit/squad-drop cascade within ~1s, so this handler
  doesn't duplicate that logic).
- Squad header row: an "Edit" button opens `EditSquadScreen` (name + limit fields, Save/Back -
  `SquadService#rename`/`#setLimit`, new methods mirroring `#create`'s validation).
- Per-member row (squads panel): "TP", "Kit" (opens `AdminPickScreen` of the kit library, force-
  assigns via `KitService#apply` + `WarState#setKit` directly - bypasses team/squad/limit checks
  entirely, an admin override not a self-pick), "Sup" (force a reconcile top-up, same as
  `/kit resupply`), "Kick" (unchanged from before).
- Both the Teams and Squads sections now scroll independently (many online players/many squads no
  longer overflow off-screen) - same shared-row-visitor pattern as before, now duplicated once more
  for teams (`forEachVisibleTeamRow` alongside `forEachVisibleSquadRow`).

**Kit Library screen** (`AdminKitsScreen` + `EditKitScreen`) - the whole kit library (not team/squad
scoped, unlike the in-game picker), icon + display name + limit + assigned teams per row, scrollable.
"Edit" opens a screen with name/limit fields (Save = one `ServerboundAdminUpdateKitPayload`) and a
live per-team toggle button (each click is its own `ServerboundAdminToggleKitTeamPayload`, since
there's no batching benefit). "Delete" removes the kit file and drops it from every team mapping,
same as `/kit delete`.

**Scarce Weapons screen** (`AdminScarceScreen`) - lists everything currently marked scarce (icon +
name) with a "Remove" button per row, and a "Mark item in my hand" button. Marking still requires
physically holding the item, same constraint as `/kit scarce setscarce` - this is a UI wrapper
around the existing rule, not a removal of it (a name alone can't specify a TACZ gun's identity).

**Round/zone conveniences on the event screen**: `-5m`/`+5m` buttons adjust a running war's clock
(`WarState#adjustRoundEnd`, new); a small field + "Set Cap" button rewrites `round.ticketCap` at
runtime (`ModConfigSpec.ConfigValue#set`, persists to the toml); "TP to Zone" teleports the admin to
the zone center; "Reset Tickets" rezeroes every team's tickets without ending the war. A capture-zone
holder line also flashes briefly whenever it changes while the screen is open, so a change registers
even if the admin isn't reading the exact numbers every second.

**Generic reusable picker** (`AdminPickScreen`) - a small "pick one of these" overlay (wraps into
columns if there are many entries) shared by the Move-to-team and Set-Kit actions, rather than each
getting its own bespoke screen.

**Deliberately not built**, to keep scope sane: move-to-squad has no dedicated button (kick, then
let the player self-rejoin via `/squad join`, covers the same outcome); moving an OFFLINE player
between teams/squads (both actions require a live `ServerPlayer`, unlike kick which works offline);
kick-from-server (explicitly excluded by the user - "not needed").

## Known gaps / next

- No point add/remove from either panel - still `/war point add|remove` (adding needs you standing at
  the location anyway). The panel does offer a per-point `TP`.
- Not yet tested in-game (compiles + assembles clean in both projects as of 2026-09-03).
