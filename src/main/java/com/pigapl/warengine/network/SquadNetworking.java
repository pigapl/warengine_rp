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
     * Called from BOTH the payload handlers here and {@code SquadCommand}, so the two paths can never
     * disagree about what gets pushed. Nobody outside the old and new squads is affected.
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
