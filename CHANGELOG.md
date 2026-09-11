# Changelog

## 0.2.1 - 2026-09-12 - fixes after event 1

Everything changed since the event 1 build (`v0.1.0`). 0.2.0 was only the version bump that opened
this cycle.

**Compatibility:** server and every player need **both 0.2.1 jars**. A client running an older
version is refused at login with a version error instead of breaking later (the kit network
protocol went from version 1 to 2).

### Scarce weapons (RPG / sniper / LMG)

- **Every round now tops players up** to their kit's amount. Before, if you still held even one
  of a scarce item, the next round gave you none of it (3 of 60 rounds left = stuck at 3).
- **Fixed: the "you already have it" check ignored the offhand and armor slots.** An RPG parked in
  the offhand at round start got you a second one. Offhand and armor now count everywhere.
- **Inventory full at round start: nothing is dropped any more.** The weapon is held for you, and
  the normal resupply countdown runs (5s grace, then "INVENTORY FULL - drop N more"). It arrives
  the moment there is room. If you still haven't made room, the countdown repeats, so the weapon
  is never lost. Survives a disconnect, not a server restart.
- **Players are told what they got** at round start, in chat: "Round start - you received: m95,
  60x 50bmg ammo". Weapons are named by their gun/ammo id.
- **Players bumped for being over their squad's kit limit are told why** and the kit menu reopens.
- A scarce armor or offhand item whose slot is already taken now goes into the inventory instead of
  being silently skipped.

### Kit switching during a war

- **Confirm popup** when picking a kit mid-war would cost you something: "Switch kit during the
  war? You will LOSE: ... / You will NOT get: ... - scarce weapons are only handed out at round
  start." Buttons: "Switch anyway" / "Cancel" (Cancel goes back to the kit menu). Before the war
  starts, nothing asks.
- Same check on the command: `/kit <id>` explains what you'd lose, `/kit <id> confirm` does it.

### Death resupply

- **Fixed: ammo in the offhand was not counted**, so every death handed out an extra stack on top.
  Offhand and armor now count. The RPG reload trick (round in the offhand) still works - that round
  counts as already held.
- `/kit check` counts offhand and armor too.

### Menus

- **Menu key is now O** (was N). The key got a new internal id, so everyone's old saved N is
  dropped and O applies. Anyone who had rebound it to a custom key is reset to O once.
- **Create Squad:** the Create button is now on the right, Back on the left (people kept hitting
  the wrong one). Labels above the fields: "Squad name" and "Max players (1-100)". The name box
  hint now reads "e.g. Alpha".

### Admin panel (`/warstate admin` -> Teams & Squads)

- **New "Home" button** on each player row in the Teams list: sends that player to their team's
  base. The admin gets a confirmation (or "has no base set"), the player gets "An admin sent you
  back to your team's base."
- **Far from base highlight:** before a war starts, a player outside their base's kit radius shows
  in orange with the distance, e.g. "Steve (87m from base)". Off during the war.
- **Alternating row colours** for players (white / light grey) in both lists, so neighbours are
  easy to tell apart. Offline squad members stay dark grey.
- The per-player TP button in the Teams list is narrower to make room for Home.
- Long player lines are now cut off before the buttons instead of running underneath them.

### Server log (so the next event's log can answer "why didn't X get Y")

- Every kit pick: `[kit] X picked 'y' (was 'z') during a war`.
- Every admin force-give (panel "Kit" button and `/kit give`): who, to whom, old kit.
- At round start: the scarce list, then one line per player - gave / already had / owed because
  the inventory was full - plus who had no kit at all and whose kit has nothing scarce.
- Owed scarce items being handed over later.
- Every live ticket-cap change: `[war] X set ticket cap 500 -> 7500`.
- Every admin "Home": `[base] X sent Y to their team base`.

## 0.2.0 - 2026-09-11

- Version bump only, to start the event 2 cycle. Event 1 ran on `0.1.0` (git tag `v0.1.0`).
