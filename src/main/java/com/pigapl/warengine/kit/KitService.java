package com.pigapl.warengine.kit;

import com.pigapl.warengine.WarConfig;
import com.pigapl.warengine.WarEngine;
import com.pigapl.warengine.network.KitNetworking;
import com.pigapl.warengine.state.WarState;
import com.pigapl.warengine.team.TeamService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class KitService {

    private KitService() {}


    public static KitDefinition capture(ServerPlayer player) {
        Inventory inv = player.getInventory();

        List<ItemStack> armor = new ArrayList<>(4);
        for (int slot = 0; slot < 4; slot++) {
            armor.add(inv.armor.get(slot).copy());
        }
        ItemStack offhand = inv.offhand.get(0).copy();

        List<ItemStack> main = new ArrayList<>();
        for (ItemStack stack : inv.items) {
            if (!stack.isEmpty()) {
                main.add(stack.copy());
            }
        }
        return new KitDefinition(armor, offhand, main);
    }


    public enum AssignResult {
        OK, UNKNOWN_KIT, NO_TEAM, NO_SQUAD, NOT_ALLOWED, NOT_RESERVED, LIMIT_REACHED, TOO_FAR_FROM_BASE
    }

    /**
     * ONLINE holders only - an offline player frees their slot and re-takes it on rejoin. Squad-scoped;
     * kit ACCESS is team-scoped via {@link TeamKits}.
     */
    public static int countUsing(MinecraftServer server, String squadId, String kitId) {
        return countUsing(server, squadId, kitId, null);
    }

    public static int countUsing(MinecraftServer server, String squadId, String kitId, UUID excluding) {
        if (squadId == null) {
            return 0;
        }
        String norm = KitStorage.normalizeId(kitId);
        WarState state = WarState.get(server);
        int count = 0;
        for (ServerPlayer other : server.getPlayerList().getPlayers()) {
            if (other.getUUID().equals(excluding)) {
                continue;
            }
            if (!squadId.equals(state.getSquad(other.getUUID()))) {
                continue;
            }
            if (norm.equals(state.getKit(other.getUUID()))) {
                count++;
            }
        }
        return count;
    }


    public static boolean kitOfferedToSquad(MinecraftServer server, String squadId, String kitId) {
        if (squadId == null) {
            return false;
        }
        WarState.SquadRecord squad = WarState.get(server).getSquadRecord(squadId);
        if (squad == null) {
            return false;
        }
        String norm = KitStorage.normalizeId(kitId);
        if (TeamKits.budgetFor(squad.team, norm) > 0) {
            return squad.kitReservations.getOrDefault(norm, 0) > 0;
        }
        return true;
    }

    public static int squadKitLimit(MinecraftServer server, String squadId, String kitId, KitDefinition kit) {
        WarState.SquadRecord squad = squadId == null ? null : WarState.get(server).getSquadRecord(squadId);
        String norm = KitStorage.normalizeId(kitId);
        if (squad != null && TeamKits.budgetFor(squad.team, norm) > 0) {
            return squad.kitReservations.getOrDefault(norm, 0);
        }
        int flat = kit.limitOrUnlimited();
        return flat <= 0 ? -1 : flat;
    }

    public static int unreservedBudget(MinecraftServer server, String team, String kitId) {
        return unreservedBudget(server, team, kitId, null);
    }

    public static int unreservedBudget(MinecraftServer server, String team, String kitId,
                                       String excludingSquadId) {
        int budget = TeamKits.budgetFor(team, KitStorage.normalizeId(kitId));
        if (budget <= 0) {
            return 0;
        }
        return budget - reservedByTeam(server, team, kitId, excludingSquadId);
    }

    public static int reservedByTeam(MinecraftServer server, String team, String kitId) {
        return reservedByTeam(server, team, kitId, null);
    }

    public static int reservedByTeam(MinecraftServer server, String team, String kitId,
                                     String excludingSquadId) {
        String norm = KitStorage.normalizeId(kitId);
        int reserved = 0;
        for (WarState.SquadRecord squad : WarState.get(server).squads().values()) {
            if (squad.id.equals(excludingSquadId)) {
                continue;
            }
            if (team != null && team.equalsIgnoreCase(squad.team)) {
                reserved += squad.kitReservations.getOrDefault(norm, 0);
            }
        }
        return reserved;
    }

    public static AssignResult assign(ServerPlayer player, String kitId, boolean bypassTeamAccess) {
        AssignResult result = check(player, kitId, bypassTeamAccess);
        if (result != AssignResult.OK) {
            return result;
        }
        String norm = KitStorage.normalizeId(kitId);
        WarState state = WarState.get(player.server);
        String old = state.getKit(player.getUUID());
        apply(player, KitStorage.get(norm).orElseThrow());
        state.setKit(player.getUUID(), norm);
        WarEngine.LOGGER.info("[kit] {} picked '{}' (was '{}'){}", player.getGameProfile().getName(), norm, old,
                state.roundActive() ? " during a war" : "");
        return AssignResult.OK;
    }

    /** Validation only, no side effects - lets callers ask for confirmation before {@link #assign}. */
    public static AssignResult check(ServerPlayer player, String kitId, boolean bypassTeamAccess) {
        String norm = KitStorage.normalizeId(kitId);
        KitDefinition kit = KitStorage.get(norm).orElse(null);
        if (kit == null) {
            return AssignResult.UNKNOWN_KIT;
        }
        if (!bypassTeamAccess) {
            String team = TeamService.getTeam(player.server, player);
            if (team == null) {
                return AssignResult.NO_TEAM;
            }
            // Before the per-kit rules: out of range NO kit is pickable, so say that, not "it's full".
            if (!com.pigapl.warengine.base.BaseService.withinKitRange(player, team)) {
                return AssignResult.TOO_FAR_FROM_BASE;
            }
            if (!TeamKits.isAllowed(team, norm)) {
                return AssignResult.NOT_ALLOWED;
            }
            String squad = com.pigapl.warengine.squad.SquadService.getSquadId(player.server, player);
            if (squad == null) {
                return AssignResult.NO_SQUAD;
            }
            if (!kitOfferedToSquad(player.server, squad, norm)) {
                return AssignResult.NOT_RESERVED;
            }
            // Re-picking the kit you already hold must not count you twice and fail on a full slot.
            boolean alreadyHasIt = norm.equals(WarState.get(player.server).getKit(player.getUUID()));
            int cap = squadKitLimit(player.server, squad, norm, kit);
            if (cap > 0 && !alreadyHasIt && countUsing(player.server, squad, norm) >= cap) {
                return AssignResult.LIMIT_REACHED;
            }
        }
        return AssignResult.OK;
    }

    /**
     * Empty = no confirmation needed. Only mid-war: a pick wipes the inventory (scarce weapons with it)
     * and the new kit's scarce items never arrive, since the sweep ran at the whistle.
     */
    public static List<String> scarceWarning(ServerPlayer player, String kitId) {
        List<String> lines = new ArrayList<>();
        KitDefinition kit = KitStorage.get(KitStorage.normalizeId(kitId)).orElse(null);
        if (kit == null || ScarceItems.isEmpty() || !WarState.get(player.server).roundActive()) {
            return lines;
        }
        if (WarConfig.CLEAR_ON_KIT_COMMAND.get()) {
            Inventory inv = player.getInventory();
            List<ItemStack> held = new ArrayList<>(inv.items);
            held.addAll(inv.armor);
            held.addAll(inv.offhand);
            List<String> lose = scarceLabels(held);
            if (!lose.isEmpty()) {
                lines.add("You will LOSE: " + String.join(", ", lose));
            }
        }
        List<ItemStack> wanted = new ArrayList<>(kit.armor());
        wanted.add(kit.offhand());
        wanted.addAll(merge(kit.inventory()));
        List<String> withheld = scarceLabels(wanted);
        if (!withheld.isEmpty()) {
            lines.add("You will NOT get: " + String.join(", ", withheld)
                    + " - scarce weapons are only handed out at round start.");
        }
        return lines;
    }

    private static List<String> scarceLabels(List<ItemStack> stacks) {
        List<String> out = new ArrayList<>();
        for (ItemStack s : stacks) {
            if (!s.isEmpty() && ScarceItems.isScarce(s)) {
                out.add(label(s));
            }
        }
        return out;
    }

    /** Server-side TACZ hover names are the generic item key, so name guns/ammo by their id instead. */
    public static String label(ItemStack s) {
        String name = s.getHoverName().getString();
        CustomData data = s.get(DataComponents.CUSTOM_DATA);
        if (data != null && !s.has(DataComponents.CUSTOM_NAME)) {
            CompoundTag tag = data.copyTag();
            if (tag.contains("GunId")) {
                name = idPath(tag.getString("GunId"));
            } else if (tag.contains("AmmoId")) {
                name = idPath(tag.getString("AmmoId")) + " ammo";
            }
        }
        return (s.getCount() > 1 ? s.getCount() + "x " : "") + name;
    }

    private static String idPath(String id) {
        int colon = id.indexOf(':');
        return colon < 0 ? id : id.substring(colon + 1);
    }


    public static void apply(ServerPlayer player, KitDefinition kit) {
        Inventory inv = player.getInventory();
        boolean clear = WarConfig.CLEAR_ON_KIT_COMMAND.get();
        if (clear) {
            inv.clearContent();
        }

        for (int slot = 0; slot < 4 && slot < kit.armor().size(); slot++) {
            ItemStack piece = kit.armor().get(slot);
            if (!piece.isEmpty() && !ScarceItems.isScarce(piece) && (clear || inv.armor.get(slot).isEmpty())) {
                inv.armor.set(slot, piece.copy());
            }
        }
        if (!kit.offhand().isEmpty() && !ScarceItems.isScarce(kit.offhand())
                && (clear || inv.offhand.get(0).isEmpty())) {
            inv.offhand.set(0, kit.offhand().copy());
        }
        for (ItemStack stack : kit.inventory()) {
            // Scarce weapons (RPG/sniper/LMG) are withheld here on purpose - they only ever enter
            // the world through the round-start sweep, see issueScarceWeapons().
            if (stack.isEmpty() || ScarceItems.isScarce(stack)) {
                continue;
            }
            ItemStack copy = stack.copy();
            insert(inv, copy);
            if (!copy.isEmpty()) {
                player.drop(copy, false);
            }
        }
        sync(player);
    }


    /**
     * The <em>only</em> place a scarce item enters the world - {@link #apply} and {@link #reconcile}
     * both withhold them. Once per round, at the whistle: tops each online holder up to the kit's
     * amount, so a weapon dropped mid-round is never replaced until the next round.
     *
     * <p>Also the one place a limited kit's cap is really enforced: {@link #assign} counts online
     * players, so a disconnect frees a slot and a squad can reach the whistle over cap. The earliest
     * {@code limit} holders keep the weapon, the rest are bumped.</p>
     */
    public static int issueScarceWeapons(MinecraftServer server) {
        if (ScarceItems.isEmpty()) {
            return 0;
        }
        OWED_SCARCE.clear();
        WarState state = WarState.get(server);
        int issued = 0;
        List<String> noKit = new ArrayList<>();
        List<String> nothingScarce = new ArrayList<>();

        Map<String, List<ServerPlayer>> groups = new LinkedHashMap<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            String kitId = state.getKit(player.getUUID());
            if (kitId == null || KitStorage.get(kitId).isEmpty()) {
                noKit.add(player.getGameProfile().getName());
                continue;
            }
            String squadId = state.getSquad(player.getUUID());
            // '\n' cannot occur in a squad id ("sqN") or a normalised kit id, so it is a safe delimiter.
            String key = (squadId == null ? player.getUUID().toString() : squadId) + "\n" + kitId;
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(player);
        }

        for (List<ServerPlayer> holders : groups.values()) {
            ServerPlayer first = holders.get(0);
            String kitId = state.getKit(first.getUUID());
            String squadId = state.getSquad(first.getUUID());
            KitDefinition kit = KitStorage.get(kitId).orElseThrow();

            int cap = squadKitLimit(server, squadId, kitId, kit);
            List<ServerPlayer> winners = holders;
            List<ServerPlayer> losers = List.of();
            if (cap > 0 && holders.size() > cap) {
                // Earliest assignment wins. UUID tie-break for determinism; an unstamped legacy
                // assignment sorts as 0, so it is favoured.
                holders.sort(Comparator
                        .comparingLong((ServerPlayer p) -> state.getKitAssignedAtMs(p.getUUID()))
                        .thenComparing(ServerPlayer::getUUID));
                winners = holders.subList(0, cap);
                losers = new ArrayList<>(holders.subList(cap, holders.size()));
            }

            for (ServerPlayer w : winners) {
                int n = issueScarceTo(w, kit, kitId);
                if (n < 0) {
                    nothingScarce.add(w.getGameProfile().getName() + " (" + kitId + ")");
                } else {
                    issued += n;
                }
            }
            if (!losers.isEmpty()) {
                for (ServerPlayer l : losers) {
                    stripKitItems(l, kit);
                    state.clearKit(l.getUUID());
                    KitNetworking.sendKitState(l);
                    l.displayClientMessage(Component.literal("Your squad had more '" + kit.displayNameOr(kitId)
                            + "' than it is allowed (" + cap + "). The earliest picks kept it, so you were "
                            + "taken off it - pick another kit.").withStyle(ChatFormatting.RED), false);
                }
                KitNetworking.sendCatalogToSquad(server, squadId);
                WarEngine.LOGGER.warn(
                        "[kit] scarce sweep: squad {} kit '{}' had {} holder(s) for cap {} - "
                        + "kept [{}], bumped [{}]",
                        squadId, kitId, holders.size(), cap,
                        nameList(winners), nameList(losers));
            }
        }
        if (!noKit.isEmpty()) {
            WarEngine.LOGGER.info("[kit] scarce sweep: no kit at the whistle: {}", String.join(", ", noKit));
        }
        if (!nothingScarce.isEmpty()) {
            WarEngine.LOGGER.info("[kit] scarce sweep: kit has nothing scarce: {}", String.join(", ", nothingScarce));
        }
        return issued;
    }

    private static String nameList(List<ServerPlayer> players) {
        return players.stream()
                .map(p -> p.getGameProfile().getName())
                .collect(Collectors.joining(", "));
    }

    private static void stripKitItems(ServerPlayer player, KitDefinition kit) {
        Inventory inv = player.getInventory();

        for (int slot = 0; slot < 4 && slot < kit.armor().size(); slot++) {
            ItemStack piece = kit.armor().get(slot);
            if (!piece.isEmpty() && !ScarceItems.isScarce(piece)
                    && !inv.armor.get(slot).isEmpty() && sameForReconcile(inv.armor.get(slot), piece)) {
                inv.armor.set(slot, ItemStack.EMPTY);
            }
        }
        if (!kit.offhand().isEmpty() && !ScarceItems.isScarce(kit.offhand())
                && !inv.offhand.get(0).isEmpty() && sameForReconcile(inv.offhand.get(0), kit.offhand())) {
            inv.offhand.set(0, ItemStack.EMPTY);
        }

        for (ItemStack want : merge(kit.inventory())) {
            if (ScarceItems.isScarce(want)) {
                continue;
            }
            int toRemove = want.getCount();
            for (int i = 0; i < inv.items.size() && toRemove > 0; i++) {
                ItemStack slot = inv.items.get(i);
                if (slot.isEmpty() || !sameForReconcile(slot, want)) {
                    continue;
                }
                int take = Math.min(toRemove, slot.getCount());
                slot.shrink(take);
                toRemove -= take;
                if (slot.isEmpty()) {
                    inv.items.set(i, ItemStack.EMPTY);
                }
            }
        }
        sync(player);
    }

    /** -1 = the kit holds nothing scarce. Otherwise stacks issued, including ones owed for lack of room. */
    private static int issueScarceTo(ServerPlayer player, KitDefinition kit, String kitId) {
        Inventory inv = player.getInventory();
        List<String> given = new ArrayList<>();
        List<String> had = new ArrayList<>();
        List<ItemStack> owed = new ArrayList<>();
        boolean any = false;

        // Top up to the kit's amount, counting EVERY slot - an RPG parked in the offhand is still held.
        for (int slot = 0; slot < 4 && slot < kit.armor().size(); slot++) {
            ItemStack piece = kit.armor().get(slot);
            if (piece.isEmpty() || !ScarceItems.isScarce(piece)) {
                continue;
            }
            any = true;
            if (countEverywhere(inv, piece) >= piece.getCount()) {
                had.add(label(piece));
            } else if (inv.armor.get(slot).isEmpty()) {
                inv.armor.set(slot, piece.copy());
                given.add(label(piece));
            } else {
                giveOrOwe(inv, piece, given, owed);
            }
        }
        ItemStack offhandWant = kit.offhand();
        if (!offhandWant.isEmpty() && ScarceItems.isScarce(offhandWant)) {
            any = true;
            int deficit = offhandWant.getCount() - countEverywhere(inv, offhandWant);
            if (deficit <= 0) {
                had.add(label(offhandWant));
            } else if (inv.offhand.get(0).isEmpty()) {
                inv.offhand.set(0, copyWithCount(offhandWant, deficit));
                given.add(label(copyWithCount(offhandWant, deficit)));
            } else {
                giveOrOwe(inv, copyWithCount(offhandWant, deficit), given, owed);
            }
        }
        for (ItemStack want : merge(kit.inventory())) {
            if (!ScarceItems.isScarce(want)) {
                continue;
            }
            any = true;
            int have = countEverywhere(inv, want);
            int deficit = want.getCount() - have;
            if (deficit <= 0) {
                had.add(label(want));
                continue;
            }
            if (have > 0) {
                had.add(label(copyWithCount(want, have)));
            }
            giveOrOwe(inv, copyWithCount(want, deficit), given, owed);
        }
        if (!any) {
            return -1;
        }
        sync(player);
        String owedText = owed.stream().map(KitService::label).collect(Collectors.joining(", "));
        WarEngine.LOGGER.info("[kit] scarce sweep: {} ({}) gave [{}] already had [{}] owed, inventory full [{}]",
                player.getGameProfile().getName(), kitId,
                String.join(", ", given), String.join(", ", had), owedText);
        if (!given.isEmpty()) {
            player.displayClientMessage(Component.literal("Round start - you received: " + String.join(", ", given))
                    .withStyle(ChatFormatting.GOLD), false);
        }
        if (!owed.isEmpty()) {
            OWED_SCARCE.computeIfAbsent(player.getUUID(), k -> new ArrayList<>()).addAll(owed);
            player.displayClientMessage(Component.literal("Inventory full - make room for: " + owedText
                    + ". It's held for you until you do.").withStyle(ChatFormatting.RED), false);
            startResupplyCountdown(player, owedCountdownSeconds(), WarConfig.RESUPPLY_GRACE_SECONDS.get());
        }
        return given.size() + owed.size();
    }

    private static int countEverywhere(Inventory inv, ItemStack want) {
        int total = countMatching(inv, want);
        for (ItemStack s : inv.armor) {
            if (!s.isEmpty() && sameForReconcile(s, want)) {
                total += s.getCount();
            }
        }
        for (ItemStack s : inv.offhand) {
            if (!s.isEmpty() && sameForReconcile(s, want)) {
                total += s.getCount();
            }
        }
        return total;
    }

    private static void giveOrOwe(Inventory inv, ItemStack want, List<String> given, List<ItemStack> owed) {
        ItemStack give = want.copy();
        int placed = insert(inv, give);
        if (placed > 0) {
            given.add(label(copyWithCount(want, placed)));
        }
        if (!give.isEmpty()) {
            owed.add(give);
        }
    }

    // Floor of 10: a 0 config would restart the countdown every tick while the player is still full.
    private static int owedCountdownSeconds() {
        return Math.max(10, WarConfig.DEFERRED_RESUPPLY_SECONDS.get());
    }

    /** Hands over what fits; the rest stays owed and is returned. */
    private static List<ItemStack> deliverOwedScarce(ServerPlayer p) {
        List<ItemStack> owed = OWED_SCARCE.remove(p.getUUID());
        if (owed == null) {
            return List.of();
        }
        List<ItemStack> left = new ArrayList<>();
        List<String> given = new ArrayList<>();
        for (ItemStack s : owed) {
            ItemStack give = s.copy();
            int placed = insert(p.getInventory(), give);
            if (placed > 0) {
                given.add(label(copyWithCount(s, placed)));
            }
            if (!give.isEmpty()) {
                left.add(give);
            }
        }
        sync(p);
        if (!given.isEmpty()) {
            p.sendSystemMessage(Component.literal("[Kit] Received: " + String.join(", ", given))
                    .withStyle(ChatFormatting.GOLD));
            WarEngine.LOGGER.info("[kit] {} received owed scarce [{}], still owed {}",
                    p.getGameProfile().getName(), String.join(", ", given), left.size());
        }
        if (!left.isEmpty()) {
            OWED_SCARCE.put(p.getUUID(), left);
        }
        return left;
    }


    private static final class Countdown {
        int graceSecondsLeft;
        int secondsLeft;
        int total;
        long nextActionTick;

        Countdown(int graceSeconds, int seconds, long firstActionTick) {
            this.graceSecondsLeft = graceSeconds;
            this.secondsLeft = seconds;
            this.total = seconds;
            this.nextActionTick = firstActionTick;
        }
    }

    private static final Map<UUID, Countdown> COUNTDOWNS = new ConcurrentHashMap<>();

    /** Round-start scarce items that didn't fit. Handed over by the resupply countdown - never dropped. */
    private static final Map<UUID, List<ItemStack>> OWED_SCARCE = new ConcurrentHashMap<>();

    /**
     * Death path: top up after respawn, keeping what they carry. Items are handed over immediately
     * (safe at {@code PlayerRespawnEvent}), but the countdown is nudged a few ticks - title packets
     * sent on the exact respawn tick are discarded while the client reloads.
     */
    public static void reconcile(ServerPlayer player, KitDefinition kit) {
        reconcile(player, kit, WarConfig.RESUPPLY_GRACE_SECONDS.get());
    }

    public static void reconcile(ServerPlayer player, KitDefinition kit, int graceSeconds) {
        int freeBefore = freeSlots(player);
        List<ItemStack> overflow = topUp(player, kit);
        WarEngine.LOGGER.info("[kit] reconcile {}: {} free main slot(s) before, {} overflow stack(s){}",
                player.getGameProfile().getName(), freeBefore, overflow.size(),
                overflow.isEmpty() ? "" : " -> " + (WarConfig.DROP_OVERFLOW.get() ? "drop" : "countdown"));

        if (overflow.isEmpty()) {
            return;
        }

        if (WarConfig.DROP_OVERFLOW.get()) {
            for (ItemStack leftover : overflow) {
                player.drop(leftover, false);
            }
            return;
        }

        int seconds = WarConfig.DEFERRED_RESUPPLY_SECONDS.get();
        if (seconds <= 0) {
            player.displayClientMessage(Component.literal("Some kit items couldn't fit in your inventory.")
                    .withStyle(ChatFormatting.RED), false);
            return;
        }
        startResupplyCountdown(player, seconds, graceSeconds);
    }

    private static int freeSlots(ServerPlayer player) {
        int free = 0;
        for (ItemStack s : player.getInventory().items) {
            if (s.isEmpty()) {
                free++;
            }
        }
        return free;
    }

    private static List<ItemStack> topUp(ServerPlayer player, KitDefinition kit) {
        Inventory inv = player.getInventory();

        for (int slot = 0; slot < 4 && slot < kit.armor().size(); slot++) {
            ItemStack piece = kit.armor().get(slot);
            if (!piece.isEmpty() && !ScarceItems.isScarce(piece) && inv.armor.get(slot).isEmpty()) {
                inv.armor.set(slot, piece.copy());
            }
        }
        if (!kit.offhand().isEmpty() && !ScarceItems.isScarce(kit.offhand()) && inv.offhand.get(0).isEmpty()) {
            inv.offhand.set(0, kit.offhand().copy());
        }

        List<ItemStack> overflow = new ArrayList<>();
        for (ItemStack want : merge(kit.inventory())) {
            // Never reconciled - dropping a scarce weapon must not mint a replacement.
            if (ScarceItems.isScarce(want)) {
                continue;
            }
            int have = countEverywhere(inv, want);
            int deficit = want.getCount() - have;
            int given = 0;
            while (deficit > 0) {
                ItemStack give = want.copy();
                give.setCount(Math.min(deficit, want.getMaxStackSize()));
                int added = insert(inv, give);
                if (added <= 0) {
                    overflow.add(copyWithCount(want, deficit));
                    break;
                }
                given += added;
                deficit -= added;
            }
            if (given > 0 || deficit > 0) {
                WarEngine.LOGGER.info("[kit]   {} x{} : had {}, gave {}, still short {}",
                        want.getHoverName().getString(), want.getCount(), have, given, Math.max(0, deficit));
            }
        }
        sync(player);
        return overflow;
    }

    /**
     * Deliberately not {@link Inventory#add}: that consults {@code Player#hasInfiniteMaterials()}, so
     * in creative it zeroes the stack and reports success with every slot full - items silently
     * voided. Reconcile has to know the truth about free space.
     */
    private static int insert(Inventory inv, ItemStack stack) {
        int placed = 0;

        for (int i = 0; i < inv.items.size() && !stack.isEmpty(); i++) {
            ItemStack slot = inv.items.get(i);
            if (slot.isEmpty() || !slot.isStackable() || !ItemStack.isSameItemSameComponents(slot, stack)) {
                continue;
            }
            int room = slot.getMaxStackSize() - slot.getCount();
            if (room <= 0) {
                continue;
            }
            int move = Math.min(room, stack.getCount());
            slot.grow(move);
            stack.shrink(move);
            placed += move;
        }

        for (int i = 0; i < inv.items.size() && !stack.isEmpty(); i++) {
            if (!inv.items.get(i).isEmpty()) {
                continue;
            }
            int move = Math.min(stack.getMaxStackSize(), stack.getCount());
            ItemStack put = stack.copy();
            put.setCount(move);
            inv.items.set(i, put);
            stack.shrink(move);
            placed += move;
        }
        return placed;
    }

    public static void debugCountdown(ServerPlayer player, int seconds) {
        startResupplyCountdown(player, seconds, 0);
    }

    public static List<Component> describeGaps(ServerPlayer player, KitDefinition kit) {
        Inventory inv = player.getInventory();
        List<Component> lines = new ArrayList<>();
        int missingTotal = 0;
        int scarceExcluded = 0;

        lines.add(Component.literal("Free main slots: " + freeSlots(player) + "/36")
                .withStyle(ChatFormatting.GRAY));

        for (int slot = 0; slot < 4 && slot < kit.armor().size(); slot++) {
            ItemStack piece = kit.armor().get(slot);
            if (piece.isEmpty()) {
                continue;
            }
            if (ScarceItems.isScarce(piece)) {
                scarceExcluded++;
                continue;
            }
            boolean gap = inv.armor.get(slot).isEmpty();
            missingTotal += gap ? 1 : 0;
            lines.add(Component.literal("  armor[" + slot + "] " + piece.getHoverName().getString()
                    + (gap ? "  MISSING" : "  ok")).withStyle(gap ? ChatFormatting.RED : ChatFormatting.GREEN));
        }
        if (!kit.offhand().isEmpty()) {
            if (ScarceItems.isScarce(kit.offhand())) {
                scarceExcluded++;
            } else {
                boolean gap = inv.offhand.get(0).isEmpty();
                missingTotal += gap ? 1 : 0;
                lines.add(Component.literal("  offhand " + kit.offhand().getHoverName().getString()
                        + (gap ? "  MISSING" : "  ok")).withStyle(gap ? ChatFormatting.RED : ChatFormatting.GREEN));
            }
        }
        for (ItemStack want : merge(kit.inventory())) {
            if (ScarceItems.isScarce(want)) {
                scarceExcluded++;
                continue;
            }
            int have = countEverywhere(inv, want);
            int deficit = want.getCount() - have;
            missingTotal += Math.max(0, deficit);
            lines.add(Component.literal("  " + want.getHoverName().getString()
                    + "  want " + want.getCount() + ", have " + have
                    + (deficit > 0 ? ", MISSING " + deficit : ""))
                    .withStyle(deficit > 0 ? ChatFormatting.RED : ChatFormatting.GREEN));
        }

        if (scarceExcluded > 0) {
            lines.add(Component.literal("  (" + scarceExcluded
                    + " scarce item(s) not shown - issued once, at round start)")
                    .withStyle(ChatFormatting.GOLD));
        }
        lines.add(missingTotal == 0
                ? Component.literal("Kit is complete - reconcile would do nothing.").withStyle(ChatFormatting.GREEN)
                : Component.literal("Missing " + missingTotal + " item(s) - reconcile would try to refill.")
                        .withStyle(ChatFormatting.YELLOW));
        return lines;
    }

    /**
     * Ticked from {@code ServerTickEvent}, not {@code TickTask} - those delays all fire at once
     * whenever the server blocks (e.g. chunk loading right after a respawn).
     */
    private static void startResupplyCountdown(ServerPlayer player, int seconds, int graceSeconds) {
        // +4 tick warm-up so the first title isn't sent while the client is still reloading the world.
        COUNTDOWNS.put(player.getUUID(),
                new Countdown(Math.max(0, graceSeconds), seconds, player.server.getTickCount() + 4L));
    }

    public static void tick(MinecraftServer server) {
        if (COUNTDOWNS.isEmpty()) {
            return;
        }
        long now = server.getTickCount();
        Iterator<Map.Entry<UUID, Countdown>> it = COUNTDOWNS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Countdown> entry = it.next();
            Countdown cd = entry.getValue();
            if (now < cd.nextActionTick) {
                continue;
            }
            ServerPlayer p = server.getPlayerList().getPlayer(entry.getKey());
            if (p == null) {
                // Kept while scarce items are owed, so a crash doesn't cost the weapon - resumes on rejoin.
                if (!OWED_SCARCE.containsKey(entry.getKey())) {
                    it.remove();
                }
                continue;
            }

            if (cd.graceSecondsLeft >= 1) {
                int grace = cd.graceSecondsLeft;
                p.sendSystemMessage(Component.literal(
                        "[Kit] Resupply in " + grace + "s - move away from spawn before dropping loot.")
                        .withStyle(ChatFormatting.GOLD));
                cd.graceSecondsLeft--;
                cd.nextActionTick = now + 20L;
                continue;
            }

            KitDefinition kit = resolveKit(server, entry.getKey());
            int stillToDrop = kit == null && !OWED_SCARCE.containsKey(entry.getKey()) ? -1 : pendingStacks(p, kit);

            if (stillToDrop == 0 || cd.secondsLeft < 1) {
                it.remove();
                finishResupply(server, p, kit);
                continue;
            }

            int remaining = cd.secondsLeft;
            String dropHint = stillToDrop < 0 ? ""
                    : "  (drop " + stillToDrop + " more)";
            p.connection.send(new ClientboundSetTitlesAnimationPacket(0, 25, 5));
            p.connection.send(new ClientboundSetTitleTextPacket(
                    Component.literal("INVENTORY FULL").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)));
            p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(
                    "Drop unneeded items - " + remaining + "s" + dropHint).withStyle(ChatFormatting.YELLOW)));
            p.sendSystemMessage(Component.literal(
                    "[Kit] Inventory full - resupply in " + remaining + "s."
                    + (stillToDrop < 0 ? "" : " Drop " + stillToDrop + " more item(s) to fit the whole kit."))
                    .withStyle(ChatFormatting.YELLOW));
            p.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS,
                    1.0f, 0.5f + (cd.total - remaining) * (0.6f / Math.max(1, cd.total)));

            cd.secondsLeft--;
            cd.nextActionTick = now + 20L;
        }
    }

    private static void finishResupply(MinecraftServer server, ServerPlayer p, KitDefinition kit) {
        boolean hadOwed = OWED_SCARCE.containsKey(p.getUUID());
        List<ItemStack> stillOwed = deliverOwedScarce(p);
        if (!stillOwed.isEmpty()) {
            if (kit != null) {
                topUp(p, kit);
            }
            // Scarce items are never dropped or forgotten - nag again until there's room.
            p.sendSystemMessage(Component.literal("[Kit] Still no room for: "
                    + stillOwed.stream().map(KitService::label).collect(Collectors.joining(", "))
                    + " - drop something to receive it.").withStyle(ChatFormatting.RED));
            startResupplyCountdown(p, owedCountdownSeconds(), 0);
            return;
        }
        if (kit == null && !hadOwed) {
            return;
        }
        List<ItemStack> stillMissing = kit == null ? List.of() : topUp(p, kit);
        p.connection.send(new ClientboundSetTitlesAnimationPacket(0, 30, 10));
        if (stillMissing.isEmpty()) {
            p.connection.send(new ClientboundSetTitleTextPacket(
                    Component.literal("RESUPPLIED").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)));
            p.connection.send(new ClientboundSetSubtitleTextPacket(Component.empty()));
            p.sendSystemMessage(Component.literal("[Kit] Resupplied.").withStyle(ChatFormatting.GREEN));
        } else {
            p.connection.send(new ClientboundSetTitleTextPacket(
                    Component.literal("KIT INCOMPLETE").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)));
            p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(
                    "Still full - " + stillMissing.size() + " item(s) not given").withStyle(ChatFormatting.RED)));
            p.sendSystemMessage(Component.literal(
                    "[Kit] Still full - " + stillMissing.size() + " item(s) not given.")
                    .withStyle(ChatFormatting.RED));
        }
    }

    private static KitDefinition resolveKit(MinecraftServer server, UUID uuid) {
        String id = WarState.get(server).getKit(uuid);
        return id == null ? null : KitStorage.get(id).orElse(null);
    }

    private static int pendingStacks(ServerPlayer player, KitDefinition kit) {
        Inventory inv = player.getInventory();
        int emptySlots = 0;
        for (ItemStack s : inv.items) {
            if (s.isEmpty()) {
                emptySlots++;
            }
        }

        int overflow = 0;
        for (ItemStack want : kit == null ? List.<ItemStack>of() : merge(kit.inventory())) {
            if (ScarceItems.isScarce(want)) {
                continue;
            }
            int deficit = want.getCount() - countEverywhere(inv, want);
            if (deficit <= 0) {
                continue;
            }
            if (want.isStackable()) {
                int partialRoom = 0;
                for (ItemStack s : inv.items) {
                    if (!s.isEmpty() && ItemStack.isSameItemSameComponents(s, want)) {
                        partialRoom += want.getMaxStackSize() - s.getCount();
                    }
                }
                deficit -= Math.min(deficit, partialRoom);
            }
            if (deficit <= 0) {
                continue;
            }
            int stacksNeeded = (deficit + want.getMaxStackSize() - 1) / want.getMaxStackSize();
            int fromEmpty = Math.min(stacksNeeded, emptySlots);
            emptySlots -= fromEmpty;
            overflow += stacksNeeded - fromEmpty;
        }
        for (ItemStack want : OWED_SCARCE.getOrDefault(player.getUUID(), List.of())) {
            int stacksNeeded = (want.getCount() + want.getMaxStackSize() - 1) / want.getMaxStackSize();
            int fromEmpty = Math.min(stacksNeeded, emptySlots);
            emptySlots -= fromEmpty;
            overflow += stacksNeeded - fromEmpty;
        }
        return overflow;
    }


    /**
     * Stackables need identical components; single items compare on a normalised identity ignoring
     * wear and runtime state. TACZ is why - every gun is the one item {@code tacz:modern_kinetic_gun}
     * with the real weapon in a {@code GunId} custom-data string. Keeping only the {@code *Id} keys
     * makes a pistol and a rifle distinct, while a half-loaded rifle still counts as already held.
     */
    // Package-visible: ScarceItems reuses this exact identity rule.
    static boolean sameForReconcile(ItemStack a, ItemStack b) {
        if (a.getMaxStackSize() > 1 && b.getMaxStackSize() > 1) {
            return ItemStack.isSameItemSameComponents(a, b);
        }
        return ItemStack.isSameItemSameComponents(identity(a), identity(b));
    }

    static ItemStack identity(ItemStack stack) {
        ItemStack copy = stack.copy();
        copy.setCount(1);
        copy.remove(DataComponents.DAMAGE);

        CustomData data = copy.get(DataComponents.CUSTOM_DATA);
        if (data != null && !data.isEmpty()) {
            CompoundTag full = data.copyTag();
            CompoundTag idOnly = new CompoundTag();
            for (String key : full.getAllKeys()) {
                if (key.endsWith("Id")) {
                    idOnly.put(key, full.get(key).copy());
                }
            }
            if (idOnly.isEmpty()) {
                copy.remove(DataComponents.CUSTOM_DATA);
            } else {
                copy.set(DataComponents.CUSTOM_DATA, CustomData.of(idOnly));
            }
        }
        return copy;
    }

    private static List<ItemStack> merge(List<ItemStack> stacks) {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack match = null;
            for (ItemStack existing : out) {
                if (sameForReconcile(existing, stack)) {
                    match = existing;
                    break;
                }
            }
            if (match == null) {
                out.add(stack.copy());
            } else {
                match.grow(stack.getCount());
            }
        }
        return out;
    }

    private static int countMatching(Inventory inv, ItemStack want) {
        int total = 0;
        for (ItemStack stack : inv.items) {
            if (!stack.isEmpty() && sameForReconcile(stack, want)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static ItemStack copyWithCount(ItemStack proto, int count) {
        ItemStack copy = proto.copy();
        copy.setCount(count);
        return copy;
    }

    private static void sync(ServerPlayer player) {
        player.containerMenu.broadcastChanges();
        player.inventoryMenu.broadcastChanges();
    }
}
