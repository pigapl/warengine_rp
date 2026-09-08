# War Engine — Kit system

Server-side mod, mod id `warengine_pigapl`, package `com.pigapl.warengine`. No client install
required: it registers no required network payloads, so vanilla / mod-less clients can still join.
(NeoForge 21.1 dropped `IExtensionPoint.DisplayTest`; "server-only" is now automatic as long as
future networking is registered as `optional`.)

## What it does

| Problem (brief) | Status |
|---|---|
| #1 Kit `/kit <class>` command | done |
| #2 Kit self-heal through death / disconnect / round reset | death = done (reconcile), disconnect = vanilla persistence, round reset = pending the round module |
| Single source of truth (`WarState`) | done — minimal, holds per-player kit assignment; team/squad/tickets/zones to be added to the same object |

## Commands

```
/kit <class>              equip a kit; wipes inventory first (config), records your class in WarState
/kit list                 list defined kits
/kit save <id>     (op)   snapshot YOUR current loadout (armor + offhand + main inv) into a kit file
/kit delete <id>   (op)   delete a kit file
/kit give <players> <id> (op)  equip a kit on other players
/kit reload        (op)   re-read kit files from disk
/kit check                report what you're missing vs your kit (non-mutating)
/kit resupply             run the reconcile top-up on yourself right now
/kit testcountdown [seconds] (op)  debug: force the resupply countdown on yourself now
/kit scarce setscarce     (op)   mark the item in your hand as scarce (RPG/sniper/LMG-style)
/kit scarce unsetscarce   (op)   unmark it
/kit scarce list          (op)   show the current scarce list
/kit scarce sweep         (op)   run the round-start scarce-weapon issuance right now
```

`list`, `save`, `delete`, `give`, `reload`, `check`, `resupply`, `testcountdown`, `scarce` are
reserved — don't name a kit one of those.

### Scarce weapons (RPG / sniper / LMG)

Global list, not per-kit — `config/warengine_pigapl/scarce.json`, `ScarceItems`. Solves "a team
ends up with 3 RPGs at the start" without a round-start wipe (which would destroy staging prep):

- A scarce item is **withheld** from `/kit <class>` (staging pick) and from respawn reconcile.
  It cannot exist in the world at all until someone deliberately issues it.
- `KitService.issueScarceWeapons(server)` — the round-start sweep — is the *only* place a scarce
  item enters the world: for every online player, for every scarce entry in their assigned kit, if
  they don't already hold a matching item, give it once. Safe to call more than once (never
  duplicates by identity), but re-running it after someone drops theirs defeats the design on
  purpose — issued once, full stop, drop it and it's gone.
- `/kit scarce sweep` runs that same method by hand, so it can be tested without the round module.
  **The round module should call `KitService.issueScarceWeapons(server)` at the actual whistle**
  (candidate hook: `RoundService.start`) instead of relying on the manual command.
- Marking is by holding the weapon and running `/kit scarce setscarce` — matches TACZ's
  `GunId`-based identity (every gun is the one item `tacz:modern_kinetic_gun`), same
  normalised-identity rule reconcile already uses (`KitService.identity`/`sameForReconcile`,
  package-visible to `ScarceItems` for this reason).
- `/kit check` and the resupply "drop M more" countdown both exclude scarce items from what they
  count as missing, so staging doesn't nag players to make room for a weapon that isn't coming yet.

### Why reconcile does its own item placement

`KitService.insert()` deliberately replaces `Inventory#add`. Vanilla's `add` consults
`Player#hasInfiniteMaterials()`, so **in creative mode it zeroes the stack and returns success
even when every slot is full** — the items are silently voided. Reconcile measures free space from
the result of the insert, so trusting `add` made it believe a full inventory had accepted
everything: no shortfall, no overflow, no countdown, and the kit items disappeared. `insert()`
merges into partial stacks then fills genuinely empty slots, and returns the real count placed.

### Diagnosing "reconcile does nothing"

`/kit check` prints, per kit entry, `want N, have M, MISSING D` plus free slot count. If everything
says `ok` there is genuinely nothing to refill — with `keepInventory true` you carry your kit
through death, so reconcile is correctly a no-op. Reconcile only acts when an item is **gone**
(ammo spent, gun dropped and its slot taken by loot, armor broken).

### Authoring a kit

1. Get into creative / grab exactly the loadout you want (armor on, gun in hotbar, ammo, meds…).
2. `/kit save rifleman`
3. It writes `config/warengine_pigapl/kits/rifleman.json`. Editable by hand afterwards; `/kit reload` to pick up edits.

Item data-components are preserved via `ItemStack.CODEC`, so TACZ guns, CBC items and Create
contraptions-in-item-form round-trip correctly.

## Death behaviour — reconcile / top-up

On respawn (`PlayerRespawnEvent`), if the player has an assigned class:

- **Keeps** everything they looted or crafted.
- **Re-adds** only missing / depleted kit items, up to the kit's quantities.
- **Armor / offhand**: fills only genuinely empty slots — a looted or damaged piece is left alone.
- **Never overfills**: if a missing item has no slot, the player gets a two-phase prompt:
  1. **Grace** (`resupplyGraceSeconds`, default 5): chat line only, no title or sound —
     `[Kit] Resupply in Ns - move away from spawn before dropping loot.` A few seconds' head
     start to leave the spawn crowd before the drop prompt appears.
  2. **Drop countdown** (`deferredResupplySeconds`, default 10): "INVENTORY FULL / Drop unneeded
     items - Ns  (drop M more)" + a matching chat line and a rising ping each second. `M`
     recalculates every second as junk is dropped; the moment it hits 0 the kit is handed over
     immediately (no waiting the clock out). Otherwise at zero it tries once and shows
     "RESUPPLIED" or "KIT INCOMPLETE - N item(s) not given"; anything that still doesn't fit is
     not given.

  Both phases are driven by `ServerTickEvent` (a real per-tick signal) — `TickTask` delays would
  all fire at once during the post-respawn chunk-load. `/kit testcountdown` and `/kit resupply`
  skip the grace phase. Set `dropOverflow = true` to drop missing items at the player's feet
  instead of the whole prompt.
- **No duplicate weapons**: single-stack items (guns, tools, attachments) match on a normalised
  identity — same item, ignoring durability and volatile runtime state, keeping only the `…Id`
  keys in `minecraft:custom_data`. TACZ puts every gun in one item (`tacz:modern_kinetic_gun`)
  and distinguishes them by a `GunId` tag, so a pistol and a rifle are correctly treated as
  different, but a half-loaded rifle still counts as "you have your rifle". (It does *not* yet
  refill rounds already chambered in the gun — see below.)

### Disconnect / crash

Vanilla already persists inventory + position across logout, so a mid-round rejoin is fine as-is.
The round-reset module will be what deliberately wipes and re-issues kits + teleports to staging.

## Config — `config/warengine_pigapl-server.toml`

```
[kit]
reconcileOnRespawn      = true    # auto top-up on respawn
deferredResupplySeconds = 10      # 0 disables the retry
clearInventoryOnCommand = true    # /kit <class> wipes first
dropOverflow            = false   # drop instead of timed retry
```

## Files

```
src/main/java/com/pigapl/warengine/
  WarEngine.java            @Mod entry
  WarConfig.java            server config spec
  GameEvents.java           RegisterCommands / ServerStarting / PlayerRespawn
  state/WarState.java       SavedData on the overworld — the single source of truth
  kit/KitDefinition.java    record + Codec (armor[4], offhand, inventory[])
  kit/KitStorage.java       load/save JSON under config/warengine_pigapl/kits/, in-memory cache
  kit/KitService.java       capture / apply / reconcile / deferred re-supply / scarce sweep
  kit/ScarceItems.java      global scarce-weapon list, config/warengine_pigapl/scarce.json
  command/KitCommand.java   /kit Brigadier tree
```

## Known gaps / next

- Ammo already inside a gun's magazine component isn't refilled on reconcile (only separate ammo
  item stacks are). Same for a partially-used TACZ ammo box — you keep your box, it is not topped
  back up to the kit amount. Needs a TACZ-aware pass if it matters.
- Overflow handling (inventory full on reconcile): the missing item is still *not* forced into
  the world if it doesn't fit after the countdown — by design ("drop stuff or the items won't
  spawn"). `dropOverflow=true` is the escape hatch. Revisit if players want a guaranteed drop.
- Kit apply doesn't preserve exact slot positions — items are packed from the first free slot.
- Round reset / staging teleport, and switching kits being locked during a live round, belong to
  the round module and aren't wired yet.
- Next module per the plan: **teams via command**, then kit UI, then team UI.
