package com.pigapl.warengine.network;

import com.pigapl.warengine.network.client.ClientPayloadHandlers;
import com.pigapl.warengine.squad.SquadService;
import com.pigapl.warengine.state.WarState;
import com.pigapl.warengine.team.TeamService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.List;

/**
 * Registers the three squad payloads and handles the two inbound ones.
 *
 * <p>Squads sit between team and kit, so a squad change also invalidates kit slot counts:
 * {@link KitAvailability#taken} is squad-scoped, so the mover's own catalog AND those of whoever
 * shares the old/new squad need refreshing - see {@link #refreshAfterSquadChange}.</p>
 */
public final class SquadNetworking {
    private SquadNetworking() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registrar.playToServer(ServerboundSelectSquadPayload.TYPE, ServerboundSelectSquadPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleSelectSquad(payload, context)));
        registrar.playToServer(ServerboundCreateSquadPayload.TYPE, ServerboundCreateSquadPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleCreateSquad(payload, context)));

        registrar.playToClient(ClientboundSquadListPayload.TYPE, ClientboundSquadListPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (FMLEnvironment.dist.isClient()) {
                        context.enqueueWork(() -> ClientPayloadHandlers.handleSquadList(payload, context));
                    }
                });
        registrar.playToClient(ClientboundSquadStatePayload.TYPE, ClientboundSquadStatePayload.STREAM_CODEC,
                (payload, context) -> {
                    if (FMLEnvironment.dist.isClient()) {
                        context.enqueueWork(() -> ClientPayloadHandlers.handleSquadState(payload, context));
                    }
                });
    }

    // ------------------------------------------------------------------ inbound (client -> server)

    private static void handleSelectSquad(ServerboundSelectSquadPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        String oldSquad = SquadService.getSquadId(player.server, player);
        if (SquadService.join(player, payload.squadId()) == SquadService.JoinResult.OK) {
            refreshAfterSquadChange(player, oldSquad, payload.squadId());
        }
    }

    private static void handleCreateSquad(ServerboundCreateSquadPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        String oldSquad = SquadService.getSquadId(player.server, player);
        if (SquadService.create(player, payload.name(), payload.limit(), payload.reservations())
                == SquadService.CreateResult.OK) {
            refreshAfterSquadChange(player, oldSquad, SquadService.getSquadId(player.server, player));
        }
    }

    /**
     * After a join/create, called from BOTH the payload handlers here and {@code SquadCommand} so the
     * two paths can never disagree about what gets pushed: confirm the mover's own state, refresh the
     * team's squad list (a roster count changed, maybe a squad appeared or vanished), and refresh kit
     * catalogs for the old squad (slot freed) and new one (slot taken). Nobody else's numbers moved.
     */
    public static void refreshAfterSquadChange(ServerPlayer player, String oldSquadId, String newSquadId) {
        MinecraftServer server = player.server;
        sendSquadState(player);
        sendSquadListToTeam(server, TeamService.getTeam(server, player));
        if (oldSquadId != null && !oldSquadId.equals(newSquadId)) {
            refreshCatalogForSquad(server, oldSquadId);
        }
        if (newSquadId != null) {
            refreshCatalogForSquad(server, newSquadId);
        }
    }

    private static void refreshCatalogForSquad(MinecraftServer server, String squadId) {
        WarState state = WarState.get(server);
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            if (squadId.equals(state.getSquad(online.getUUID()))) {
                KitNetworking.sendCatalogFor(online);
            }
        }
    }

    // ------------------------------------------------------------------ outbound (server -> client)

    /** Refreshes the squad list (and so the roster counts) for every online player on a team. */
    public static void sendSquadListToTeam(MinecraftServer server, String team) {
        if (team == null) {
            return;
        }
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            if (team.equalsIgnoreCase(TeamService.getTeam(server, online))) {
                sendSquadListFor(online);
            }
        }
    }

    /** Sends the player their current team's squads, or an empty list if they have no team. */
    public static void sendSquadListFor(ServerPlayer player) {
        String team = TeamService.getTeam(player.server, player);
        List<SquadEntry> entries = new ArrayList<>();
        if (team != null) {
            for (WarState.SquadRecord record : SquadService.squadsFor(player.server, team)) {
                entries.add(new SquadEntry(record.id, record.name,
                        SquadService.memberCount(player.server, record.id), record.limit));
            }
        }
        PacketDistributor.sendToPlayer(player, new ClientboundSquadListPayload(entries));
    }

    public static void sendSquadState(ServerPlayer player) {
        String squadId = SquadService.getSquadId(player.server, player);
        PacketDistributor.sendToPlayer(player, new ClientboundSquadStatePayload(squadId == null ? "" : squadId));
    }
}
