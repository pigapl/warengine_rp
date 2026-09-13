package com.pigapl.warengine;

import com.pigapl.warengine.base.BaseService;
import com.pigapl.warengine.command.KitCommand;
import com.pigapl.warengine.command.SquadCommand;
import com.pigapl.warengine.command.WarCommand;
import com.pigapl.warengine.command.WarStateCommand;
import com.pigapl.warengine.kit.KitDefinition;
import com.pigapl.warengine.kit.KitStorage;
import com.pigapl.warengine.kit.ScarceItems;
import com.pigapl.warengine.kit.TeamKits;
import com.pigapl.warengine.round.RoundService;
import com.pigapl.warengine.network.KitNetworking;
import com.pigapl.warengine.network.SquadNetworking;
import com.pigapl.warengine.squad.SquadService;
import com.pigapl.warengine.state.WarState;
import com.pigapl.warengine.team.TeamService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = WarEngine.MODID)
public final class GameEvents {

    private GameEvents() {}

    // Deferred a few ticks past the login event: a title sent while the client is still tearing down
    // its loading screen is discarded (same bug as KitService's "+4 tick warm-up").
    private static final Map<UUID, Long> PENDING_TEAM_LOGIN_NAG = new ConcurrentHashMap<>();

    private static final Map<UUID, String> LAST_KNOWN_TEAM = new ConcurrentHashMap<>();

    private static final String NO_TEAM = "";

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        KitCommand.register(event.getDispatcher());
        WarCommand.register(event.getDispatcher());
        SquadCommand.register(event.getDispatcher());
        WarStateCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        com.pigapl.warengine.kit.KitService.tick(event.getServer());
        RoundService.tick(event.getServer());
        onTeamNagTick(event.getServer());
        onTeamLoginNagTick(event.getServer());
        onTeamChangeTick(event.getServer());
        com.pigapl.warengine.base.BaseService.keepInBaseTick(event.getServer());
    }

    @SubscribeEvent
    public static void onLivingDeath(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RoundService.onPlayerDeath(player);
        }
    }

    /**
     * Catches a team change by ANY route. POLLED once a second, not hooked: scoreboard membership has
     * no event and we do not control every path that changes it.
     */
    private static void onTeamChangeTick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) {
            return;
        }
        // Broadcast once at the end, not per player - a mass reassign would otherwise be quadratic.
        Set<String> affectedTeams = null;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            String current = TeamService.getTeam(server, player);
            String currentKey = current == null ? NO_TEAM : current;
            String previous = LAST_KNOWN_TEAM.put(player.getUUID(), currentKey);
            if (previous == null || previous.equals(currentKey)) {
                continue;
            }

            // Vacate BEFORE touching kit state: limits are squad-scoped, so a stale squad assignment
            // would make the catalog resend below count against the wrong roster.
            String vacatedSquad = SquadService.leave(player);
            // Unconditional: commander/leader are stamped with a team, so changing side changes what
            // this player may do even when they were in no squad to vacate. Skipping the push here left
            // a stale "you may create a squad" on the client of anyone who switched teams squadless.
            SquadNetworking.sendSquadState(player);
            if (vacatedSquad != null) {
                WarEngine.LOGGER.info("[squad] {} moved teams, left squad '{}'",
                        player.getGameProfile().getName(), vacatedSquad);
            }

            // Always drop the kit, even if the new team could use it too: a "*" kit would otherwise
            // carry over without re-checking that team's limit, letting a switch push it past its cap.
            String kitId = WarState.get(server).getKit(player.getUUID());
            if (kitId != null) {
                WarState.get(server).clearKit(player.getUUID());
                // Wipe the inventory too, or switching sides carries the other faction's gear across.
                player.getInventory().clearContent();
                KitNetworking.sendKitState(player);
                WarEngine.LOGGER.info("[team] {} moved {} -> {}, cleared kit '{}' and inventory",
                        player.getGameProfile().getName(),
                        previous.isEmpty() ? "(none)" : previous,
                        currentKey.isEmpty() ? "(none)" : currentKey, kitId);
            }

            if (current == null) {
                // No team broadcast will reach them - clear their menus directly.
                KitNetworking.sendCatalogFor(player);
                SquadNetworking.sendSquadListFor(player);
            }
            if (affectedTeams == null) {
                affectedTeams = new HashSet<>();
            }
            if (!previous.isEmpty()) {
                affectedTeams.add(previous);
            }
            if (current != null) {
                affectedTeams.add(current);
            }
        }

        if (affectedTeams != null) {
            for (String team : affectedTeams) {
                KitNetworking.sendCatalogToTeam(server, team);
                SquadNetworking.sendSquadListToTeam(server, team);
            }
        }
    }

    private static void onTeamLoginNagTick(MinecraftServer server) {
        if (PENDING_TEAM_LOGIN_NAG.isEmpty()) {
            return;
        }
        long now = server.getTickCount();
        Iterator<Map.Entry<UUID, Long>> it = PENDING_TEAM_LOGIN_NAG.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();
            if (now < entry.getValue()) {
                continue;
            }
            it.remove();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            List<String> teamIds = TeamService.ids(server);
            if (player == null || teamIds.isEmpty() || TeamService.getTeam(server, player) != null) {
                continue;
            }
            player.connection.send(new ClientboundSetTitlesAnimationPacket(0, 40, 10));
            player.connection.send(new ClientboundSetTitleTextPacket(
                    Component.literal("PICK A TEAM").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
            player.connection.send(new ClientboundSetSubtitleTextPacket(
                    openMenuHint().withStyle(ChatFormatting.YELLOW)));
        }
    }

    private static void onTeamNagTick(MinecraftServer server) {
        int interval = WarConfig.TEAM_NAG_INTERVAL_SECONDS.get();
        if (interval <= 0) {
            return;
        }
        long intervalTicks = interval * 20L;
        if (server.getTickCount() % intervalTicks != 0) {
            return;
        }
        List<String> teamIds = TeamService.ids(server);
        if (teamIds.isEmpty()) {
            return;
        }
        WarState nagState = WarState.get(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (TeamService.getTeam(server, player) == null && !nagState.isExempt(player.getUUID())) {
                player.displayClientMessage(Component.literal("Pick a team  ")
                        .append(openMenuHint())
                        .withStyle(ChatFormatting.YELLOW), true);
            }
        }
    }

    /**
     * Just the key, in brackets. Resolves to whatever the reader actually bound, so a rebind never makes
     * us lie; the id is the CLIENT addon's mapping - keep it in step with KeyBindings.OPEN_MENU.
     * Deliberately wordless: most players here do not read English.
     */
    private static MutableComponent openMenuHint() {
        return Component.literal("[")
                .append(Component.keybind("key.warengine_pigapl_client.menu"))
                .append("]");
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        int count = KitStorage.loadAll(event.getServer());
        WarEngine.LOGGER.info("Loaded {} kit(s) from {}: {}", count, KitStorage.dir(), KitStorage.ids());
        int teams = TeamKits.loadAll();
        WarEngine.LOGGER.info("Loaded team kit mapping for {} team(s) from {}", teams, TeamKits.file());
        int scarce = ScarceItems.loadAll(event.getServer());
        WarEngine.LOGGER.info("Loaded {} scarce item(s) from {}", scarce, ScarceItems.file());
        TeamKits.warnAboutMissingKits();
        TeamKits.warnAboutMissingTeams(event.getServer());
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        KitNetworking.sendKitState(player);
        SquadNetworking.sendSquadState(player);
        // The HUD feed is push-on-change, so a joining player needs one unconditional send to start
        // from - before the team check, since the points HUD is not gated on having a team.
        RoundService.sendPointsTo(player);
        if (TeamService.getTeam(player.server, player) == null) {
            PENDING_TEAM_LOGIN_NAG.put(player.getUUID(), player.server.getTickCount() + 20L);
            return;
        }
        // Reconnect onto an existing team: push the squad list and catalog now, since the client only
        // re-sends SelectTeam/SelectSquad for state it picked this session.
        SquadNetworking.sendSquadListFor(player);
        KitNetworking.sendCatalogFor(player);
    }

    /**
     * Frees the leaver's kit slot in everyone else's view - limits count online players only. Squad
     * MEMBERSHIP is untouched by logging out, so no squad list is resent.
     */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.server;
        LAST_KNOWN_TEAM.remove(player.getUUID());
        PENDING_TEAM_LOGIN_NAG.remove(player.getUUID());
        String squad = WarState.get(server).getSquad(player.getUUID());
        if (squad != null) {
            for (ServerPlayer online : server.getPlayerList().getPlayers()) {
                if (online != player && squad.equals(WarState.get(server).getSquad(online.getUUID()))) {
                    KitNetworking.sendCatalogFor(online, player.getUUID());
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // No base set = vanilla respawn, unchanged. Deliberately ahead of the RECONCILE_ON_RESPAWN
        // gate: where you respawn and whether your kit tops up are separate decisions.
        BaseService.teleportToOwnBase(player);

        if (!WarConfig.RECONCILE_ON_RESPAWN.get()) {
            return;
        }
        String kitId = WarState.get(player.server).getKit(player.getUUID());
        WarEngine.LOGGER.info("[kit] respawn {}: assigned kit = {}", player.getGameProfile().getName(), kitId);
        if (kitId == null) {
            return;
        }
        KitStorage.get(kitId).ifPresentOrElse(
                kit -> {
                    com.pigapl.warengine.kit.KitService.reconcile(player, kit);
                    explainMissingScarce(player, kit);
                },
                () -> WarEngine.LOGGER.warn("Player {} is assigned kit '{}' which no longer exists",
                        player.getGameProfile().getName(), kitId));
    }

    /**
     * "Many players had no ammo" after event 1 was this, working as designed and never explained: a
     * respawn tops up normal ammo but never rationed gear. Said only when they are actually short, and
     * in the same four words the kit picker uses - most players here do not read English.
     */
    private static void explainMissingScarce(ServerPlayer player, KitDefinition kit) {
        if (!WarState.get(player.server).roundActive()) {
            return;
        }
        List<String> missing = com.pigapl.warengine.kit.KitService.missingScarce(player, kit);
        if (missing.isEmpty()) {
            return;
        }
        player.displayClientMessage(Component.literal("Round start only: " + String.join(", ", missing))
                .withStyle(ChatFormatting.YELLOW), false);
    }
}
