# Team bases

One saved position per team. Setting a base does three things for that team:

1. **Respawn** - its players respawn at the base instead of world spawn.
2. **Mass teleport** - "TP All to Bases" sends everyone to their own team's base.
3. **Kit range** - its players may only pick or change a kit within `bases.kitRadius`
   (default **40** blocks) of it.

A team with **no base set is completely unrestricted** - it respawns vanilla-style and can pick kits
anywhere. Nothing here happens until you actually set a base, so an event that doesn't want bases
just doesn't set them.

## Setting them up

Walk to where the base should be, then either:

- **Admin panel** - `/warstate admin` -> **Teams & Squads**. Each team row has:
  - `Base` - sets that team's base to **where you are standing right now**
  - `B>` - teleports you to that base (to check it)
  - `Clr` - removes it
- **Commands** - `/warstate base set <team>` / `clear <team>` / `tp <team>` / `list` / `tpall`

The team row shows `base 120 64 -30` or `no base`, so you can see at a glance which teams are set up.

Both paths call the same `BaseService`, so they can't enforce different rules.

## Moving everyone to their bases

`/warstate admin` -> **TP All to Bases** (bottom row), or `/warstate base tpall`.

Every online player goes to their own team's base. Players with no team, or on a team with no base,
stay where they are - the button reports how many were actually moved.

## The kit-range rule

Picking a kit is refused with a red chat line ("Too far from your team's base to change kit - get
within 40 blocks of it") when the player is outside the radius, or in another dimension from it.

- Applies to **both** the kit picker UI and `/kit <id>` - they share one check in
  `KitService.assign`.
- **Ops bypass it**, like every other kit rule (so you can test kits anywhere).
- Respawn top-up and `/kit resupply` do **not** check range - they restore a kit you already own,
  they don't hand out a new one.
- `bases.kitRadius = 0` disables the distance check entirely while leaving respawn and TP working.

## Where it's stored

In `WarState` (the world's own save data), next to the capture zone - **not** in
`config/warengine_pigapl/teamkits.json`. Coordinates are per-map: last week's base means nothing on
a fresh world, so bases deliberately do not survive a map swap the way the kit library and team
setup do. On a fresh map, set them again.
