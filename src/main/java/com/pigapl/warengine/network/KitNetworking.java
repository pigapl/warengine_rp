package com.pigapl.warengine.network;

import com.pigapl.warengine.WarConfig;
import com.pigapl.warengine.WarEngine;
import com.pigapl.warengine.kit.KitService;
import com.pigapl.warengine.kit.KitStorage;
import com.pigapl.warengine.kit.TeamKits;
import com.pigapl.warengine.network.client.ClientPayloadHandlers;
import com.pigapl.warengine.state.WarState;
import com.pigapl.warengine.team.TeamService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Registers every custom payload and handles the inbound ones.
 *
 * <p>All payloads are registered on BOTH dists - the handshake requires both sides to agree on which
 * channels exist. The {@code Dist.CLIENT} check goes inside the S2C handler bodies, never around the
 * registration, so a dedicated server never resolves {@link ClientPayloadHandlers}.</p>
 */
public final class KitNetworking {
    private KitNetworking() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registrar.playToServer(ServerboundSelectTeamPayload.TYPE, ServerboundSelectTeamPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleSelectTeam(payload, context)));
        registrar.playToServer(ServerboundSelectKitPayload.TYPE, ServerboundSelectKitPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleSelectKit(payload, context)));

        registrar.playToClient(ClientboundKitCatalogPayload.TYPE, ClientboundKitCatalogPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (FMLEnvironment.dist.isClient()) {
                        context.enqueueWork(() -> ClientPayloadHandlers.handleKitCatalog(payload, context));
                    }
                });
        registrar.playToClient(ClientboundKitStatePayload.TYPE, ClientboundKitStatePayload.STREAM_CODEC,
                (payload, context) -> {
                    if (FMLEnvironment.dist.isClient()) {
                        context.enqueueWork(() -> ClientPayloadHandlers.handleKitState(payload, context));
                    }
                });

        registrar.playToServer(ServerboundRequestKitBudgetPayload.TYPE, ServerboundRequestKitBudgetPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleRequestKitBudget(context)));
        registrar.playToClient(ClientboundKitBudgetPayload.TYPE, ClientboundKitBudgetPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (FMLEnvironment.dist.isClient()) {
                        context.enqueueWork(() -> ClientPayloadHandlers.handleKitBudget(payload, context));
                    }
                });
    }


    private static void handleSelectTeam(ServerboundSelectTeamPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        TeamService.AssignResult result = TeamService.assign(player.server, player, payload.teamId());
        if (result == TeamService.AssignResult.OK) {
            // Empty until a squad is picked too, but sent anyway so a stale old-team catalog
            // never lingers in the client cache.
            sendCatalogFor(player);
            SquadNetworking.sendSquadListFor(player);
        }
    }

    private static void handleRequestKitBudget(IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            sendKitBudgetFor(player);
        }
    }

    private static void handleSelectKit(ServerboundSelectKitPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        boolean bypass = player.createCommandSourceStack().hasPermission(2);
        KitService.AssignResult result = KitService.assign(player, payload.kitId(), bypass);
        if (result == KitService.AssignResult.OK) {
            sendKitState(player);
            // Taking a limited kit changes the remaining slots for the rest of the SQUAD only -
            // the limit is squad-scoped now, see KitService#countUsing.
            sendCatalogToSquad(player.server, WarState.get(player.server).getSquad(player.getUUID()));
            return;
        }
        // A rejected pick must say why, or the picker reads as a dead button - most visibly for
        // TOO_FAR_FROM_BASE, where every kit refuses until the player walks back to base.
        Component reason = switch (result) {
            case TOO_FAR_FROM_BASE -> Component.literal("Too far from your team's base to change kit - get within "
                    + Math.round(WarConfig.BASE_KIT_RADIUS.get()) + " blocks of it.");
            case LIMIT_REACHED -> Component.literal("That kit is full in your squad.");
            case NOT_RESERVED -> Component.literal("Your squad has none of that kit reserved.");
            case NOT_ALLOWED -> Component.literal("Your team cannot use that kit.");
            case NO_TEAM -> Component.literal("Join a team first.");
            case NO_SQUAD -> Component.literal("Join or create a squad first.");
            case UNKNOWN_KIT -> Component.literal("That kit no longer exists.");
            case OK -> null;
        };
        if (reason != null) {
            player.displayClientMessage(reason.copy().withStyle(ChatFormatting.RED), false);
        }
    }

    /** For team-wide changes only. A taken/dropped limited kit is squad-scoped - use {@link #sendCatalogToSquad}. */
    public static void sendCatalogToTeam(MinecraftServer server, String team) {
        if (team == null) {
            return;
        }
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            if (team.equalsIgnoreCase(TeamService.getTeam(server, online))) {
                sendCatalogFor(online);
            }
        }
    }

    public static void sendCatalogToSquad(MinecraftServer server, String squadId) {
        if (squadId == null) {
            return;
        }
        WarState state = WarState.get(server);
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            if (squadId.equals(state.getSquad(online.getUUID()))) {
                sendCatalogFor(online);
            }
        }
    }


    public static void sendCatalogFor(ServerPlayer player) {
        sendCatalogFor(player, null);
    }

    /** Empty with no team OR no squad - squad gates the kit step, so a stale picker never shows. */
    public static void sendCatalogFor(ServerPlayer player, UUID excludeFromCounts) {
        String team = TeamService.getTeam(player.server, player);
        String squad = team == null ? null
                : com.pigapl.warengine.squad.SquadService.getSquadId(player.server, player);
        List<KitCatalogEntry> entries = new ArrayList<>();
        if (team != null && squad != null) {
            for (String id : TeamKits.kitsFor(team)) {
                if (!KitService.kitOfferedToSquad(player.server, squad, id)) {
                    continue;
                }
                KitStorage.get(id).ifPresent(kit -> {
                    int cap = KitService.squadKitLimit(player.server, squad, id, kit);
                    entries.add(new KitCatalogEntry(
                            id, kit.displayNameOr(id), kit.iconOrGuess(), kit.description().orElse(""),
                            new KitLoadout(kit.armor(), kit.offhand(), kit.inventory()),
                            new KitAvailability(cap < 0 ? 0 : cap,
                                    KitService.countUsing(player.server, squad, id, excludeFromCounts))));
                });
            }
        }
        PacketDistributor.sendToPlayer(player, new ClientboundKitCatalogPayload(entries));
    }

    public static void sendKitState(ServerPlayer player) {
        String kitId = WarState.get(player.server).getKit(player.getUUID());
        PacketDistributor.sendToPlayer(player, new ClientboundKitStatePayload(kitId == null ? "" : kitId));
    }

    public static void sendKitBudgetFor(ServerPlayer player) {
        String team = TeamService.getTeam(player.server, player);
        List<KitBudgetEntry> entries = new ArrayList<>();
        if (team != null) {
            TeamKits.budgetsOf(team).forEach((kitId, total) ->
                    KitStorage.get(kitId).ifPresent(kit -> entries.add(new KitBudgetEntry(
                            kitId, kit.displayNameOr(kitId), kit.iconOrGuess(), total,
                            KitService.unreservedBudget(player.server, team, kitId)))));
        }
        PacketDistributor.sendToPlayer(player, new ClientboundKitBudgetPayload(entries));
    }
}
