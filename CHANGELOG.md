# Changelog

## 0.2.1 - 2026-09-13 - fixes after event 1, commanders, base lock

Everything changed since the event 1 build (`v0.1.0`). 0.2.0 was only the version bump that opened
this cycle.

**Compatibility:** server and every player need **both 0.2.1 jars, built from the same commit**.
The network protocols moved during this version (kit 1 -> 3, squad 1 -> 2, admin 1 -> 2), so a
client built earlier in the 0.2.1 cycle is refused at login even though its jar has the same name.
Rebuild both and hand them out together.

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

### Fixes

- **Re-picking the squad you are already in no longer destroys it.** If you were its only member,
  selecting your own squad again ran leave-then-rejoin - and leaving as the last member deletes the
  squad, so you ended up pointed at a squad that no longer existed. Every kit then read as
  "not reserved" (the lookup failed, so nothing was on offer) and the squad vanished from the list.
  Coming back from Manage and tapping your own squad hit this every time.
- A deleted squad now clears itself off its members, and a player pointing at a squad that is gone is
  repaired on read - so worlds already in this state fix themselves on load.
- **Fixed a client crash when the kit budget list changed under an open Create Squad screen** (moving
  a player to another team did it). The screen cached a row count in `init()` and indexed the live
  list with it in `render()`.
- Role changes now reach the player immediately: granting `Cmd`/`Ldr` ungreys their Create Squad
  button and shows Manage without them doing anything. Handing over the commander role updates both
  players. Changing team always refreshes what you are allowed to do, squad or no squad.

### Kits: budgets are the only cap

- **The per-kit limit is gone from the Edit Kit screen.** Team budgets plus squad reservations are the
  whole story now; a kit with no budget is unlimited. The old `limit` field in kit JSON is ignored and
  no longer written, so nothing changes for existing kits (none had one set).

### Commanders and squad leaders

Event 1: "a random was taking most kits" - anyone could found a squad and reserve the team's whole
budget. There is now a chain: admin -> commander -> squad leader.

- **Commander** (per team): founds squads, and will appoint leaders and edit any squad on the team.
  Assigned by an admin with `Cmd` on the player row, gold when on.
- **Squad leader:** may found a squad and will edit the one they lead. Assigned with `Ldr`, blue
  when on.
- **Everyone else** joins a squad and picks a kit, as before. Trying to found one now says
  "Commanders and squad leaders only".
- Both roles are stored **with the team they were granted for**, so moving a player to another team
  drops the role instead of quietly making them commander of their new side. Both survive a restart.
- Whoever founds a squad becomes its leader, unless a commander founded it - then it is left
  leaderless for the commander to appoint into.

**Part 2 - the Manage screen.** A **Manage** button on the squad screen, greyed out with
"Commanders and squad leaders only" for everyone else, same as Create Squad. A leader sees the squad they lead; a commander sees every squad on the team
and picks between them.

- Rename the squad and change its player limit. **Save turns yellow while there are unsaved
  edits**, and goes back to normal once the change has been saved.
- **Kick** a member, and **Lead** to make one the squad leader (commanders only - a leader cannot
  appoint their own successor).
- **Change reservations after creation**, with `- N +` steppers capped by what the team has left
  unreserved. Until now these could only be set when the squad was founded, or with `/squad reserve`.
  Editable at any time, war or not; the round-start sweep reads whatever the numbers are when it runs.
- **Command** hands the commander role to another player on your team - you stop being commander.
- **Create Squad** is greyed out with "Commanders and squad leaders only" for everyone else.
- A squad's roster and reservations are only sent to players who may edit that squad, so nobody
  learns them from the wire.

### Telling players the rules

Nothing below changes how the game works - event 1 played by these rules and nobody was ever told
them. Kept to as few words as possible on purpose: most players on this server do not read English,
so item names, numbers and the key itself carry the meaning, not sentences.

- **The kit picker names a kit's rationed gear:** `Round start only:` and the item names. That is the
  whole explanation of why the sniper kit hands you no sniper yet. The names come from the server,
  since a TACZ gun has no useful name client-side.
- **On respawn**, a player actually short of rationed gear gets the same four words and the names:
  `Round start only: M95, 60x .50 BMG`. This is the "many players had no ammo" report - resupply was
  working, it just never refills rationed items, and nothing said so.
- **At the start of a round:** `Points: A, B, C - stand inside to capture`. One line. Who holds a
  point, capture progress and the score target are all on the HUD already.
- **"Not reserved by your squad"** on a kit card that is greyed out, instead of a sentence.
- **The team nag is now just the key** in brackets, resolved to whatever that player has bound.

### Menus

- **Only the team menu opens by itself now.** Squad and kit no longer throw a screen at you the
  moment you have a team - you open them when you are ready, with the menu key, before the whistle.
  The chain is unchanged: the key always gives you the next screen you still owe (team, then squad,
  then kit).
- **The menu steps forward while it is open.** Pick a team and the squad screen appears, pick a
  squad and the kit screen appears (creating a squad does too, since it joins you). Esc drops out at
  any point and nothing reopens.
- **Every picker shows the whole flow** as a `Team > Squad > Kit` line under the title, with what you
  already picked filled in, so the steps you still owe are visible from the first screen.
- **Kits your squad did not reserve are shown greyed out** as "not reserved", instead of being
  missing from the menu. At event 1 the budgeted sniper and LMG simply were not there for most
  squads, which read as the mod being broken. The tooltip says a squad claims them at creation.
- **One exception:** if the round-start sweep takes your kit away (your squad had more of a limited
  kit than it is allowed), the kit picker still opens by itself - the state changed under you.
- **Menu key is now O** (was N). The key got a new internal id, so everyone's old saved N is
  dropped and O applies. Anyone who had rebound it to a custom key is reset to O once.
- **Create Squad:** the Create button is now on the right, Back on the left (people kept hitting
  the wrong one). Labels above the fields: "Squad name" and "Max players (1-100)". The name box
  hint now reads "e.g. Alpha".

### Base lock (keep players in base before the war)

- **New toggle on the event screen: `Base lock: ON/OFF`**, off by default and remembered across a
  restart. Turning it on or off is announced in chat.
- While it is on and no war is running, a player who leaves their own team's base sees `BASE 5`
  counting down on their action bar, and is **teleported back** if they are still out at 0. Stepping
  back inside cancels it. Leaving the dimension counts as out.
- Skipped for players in admin mode (`Ex`), spectators, players with no team, and teams with no base.
- Does nothing during a war. It applies again between rounds if left on.
- **One base size for everything:** the same radius decides where kits can be picked and where the
  lock pulls you back. Each base has its own size, changed with `-` / `+` on the team's row in
  Teams & Squads (5 blocks per click, 5-500). A base with no size of its own uses `bases.kitRadius`
  (40). Re-setting a base's position keeps its size. Countdown length: `bases.keepGraceSeconds` (5).
- **The base is marked on the ground** with a ring in the team's colour, like a capture point but
  without the centre pillar. Players see their own team's base; admins see every base. Shown only
  before the war - it disappears when the war starts (the base still limits kit picking).

### Admin panel - the UI pass

- **Every per-player action is the same row everywhere.** Teams and Squads both give you
  `TP / Home / Team / Sqd / Kit / Sup / Kick` in that order, at the same widths, with a tooltip on
  each. Before, TP-to-base only existed under Teams and TP-to-player only under Squads.
- **Move a player to another squad** from either panel - the action existed on the server and had no
  button. Kick is greyed out for anyone not in a squad; the actions that need a body in the world are
  greyed out for offline players.
- **Admin mode, per player** (`Ex` on the player row, green when on). For an admin who is also
  playing: they are never flagged as far from base, the base radius never blocks them picking a kit,
  and the "pick a team" nag skips them. Nothing else changes - it is not a permission. The player is
  told "Admin mode on/off" on their action bar, and it survives a restart.
- **"Mark several..." on the Scarce Weapons screen.** An item grid you tick, instead of holding one
  item and clicking once per item. Two tabs: **My inventory** (the fast path - grab a loadout, open,
  tick) and **All kit items** (every distinct item used by any saved kit, to catch one sitting in a
  kit you forgot). Already-scarce items open ticked, so it doubles as a review of the whole list.
  The screen does not poll while it is open, so a server reply can never wipe what you ticked, and
  the whole edit is one save.

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
