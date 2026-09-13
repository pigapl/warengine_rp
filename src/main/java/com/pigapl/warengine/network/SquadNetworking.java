package com.pigapl.warengine.network;

import com.pigapl.warengine.network.client.ClientPayloadHandlers;
import com.pigapl.warengine.kit.KitStorage;
import com.pigapl.warengine.kit.TeamKits;
import com.pigapl.warengine.squad.SquadService;
import com.pigapl.warengine.state.WarState;
import com.pigapl.warengine.team.RoleService;
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

public final class SquadNetworking {
    private SquadNetworking() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        // "2": squad_list entries gained the manage block, plus three new C2S management payloads.
        PayloadRegistrar registrar = event.registrar("2");

        registrar.playToServer(ServerboundSelectSquadPayload.TYPE, ServerboundSelectSquadPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleSelectSquad(payload, context)));
        registrar.playToServer(ServerboundCreateSquadPayload.TYPE, ServerboundCreateSquadPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleCreateSquad(payload, context)));
        registrar.playToServer(ServerboundSquadEditPayload.TYPE, ServerboundSquadEditPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleSquadEdit(payload, context)));
        registrar.playToServer(ServerboundSquadReservePayload.TYPE, ServerboundSquadReservePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleSquadReserve(payload, context)));
        registrar.playToServer(ServerboundSquadMemberActionPayload.TYPE,
                ServerboundSquadMemberActionPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleMemberAction(payload, context)));

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
        SquadService.CreateResult result =
                SquadService.create(player, payload.name(), payload.limit(), payload.reservations());
        if (result == SquadService.CreateResult.OK) {
            refreshAfterSquadChange(player, oldSquad, SquadService.getSquadId(player.server, player));
        } else if (result == SquadService.CreateResult.NOT_ALLOWED) {
            player.displayClientMessage(Component.literal("Commanders and squad leaders only")
                    .withStyle(ChatFormatting.RED), false);
        }
    }

    private static void handleSquadEdit(ServerboundSquadEditPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)
                || !RoleService.canEditSquad(player.server, player, payload.squadId())) {
            return;
        }
        MinecraftServer server = player.server;
        SquadService.rename(server, payload.squadId(), payload.name());
        SquadService.setLimit(server, payload.squadId(), payload.limit());
        sendSquadListToTeam(server, TeamService.getTeam(server, player));
    }

    private static void handleSquadReserve(ServerboundSquadReservePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)
                || !RoleService.canEditSquad(player.server, player, payload.squadId())) {
            return;
        }
        MinecraftServer server = player.server;
        SquadService.UpdateResult result =
                SquadService.setReservation(server, payload.squadId(), payload.kitId(), payload.count());
        if (result == SquadService.UpdateResult.BUDGET_EXCEEDED) {
            player.displayClientMessage(Component.literal("No budget left").withStyle(ChatFormatting.RED), true);
        }
        sendSquadListToTeam(server, TeamService.getTeam(server, player));
        // The reservation IS the squad's cap for a budgeted kit, so every member's picker is now stale.
        refreshCatalogForSquad(server, payload.squadId());
    }

    private static void handleMemberAction(ServerboundSquadMemberActionPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.server;
        String action = payload.action();

        if (ServerboundSquadMemberActionPayload.HAND_OVER.equals(action)) {
            if (!RoleService.canHandOverTo(server, player, payload.target())) {
                return;
            }
            WarState st = WarState.get(server);
            String team = TeamService.getTeam(server, player);
            st.setCommanderOf(player.getUUID(), null);
            st.setCommanderOf(payload.target(), team);
            ServerPlayer newCommander = server.getPlayerList().getPlayer(payload.target());
            if (newCommander != null) {
                newCommander.displayClientMessage(
                        Component.literal("Commander ON").withStyle(ChatFormatting.GOLD), true);
                sendSquadState(newCommander);
            }
            sendSquadState(player);   // the old commander just lost canCreate/canEdit
            sendSquadListToTeam(server, team);
            return;
        }

        if (!RoleService.canEditSquad(server, player, payload.squadId())) {
            return;
        }
        if (ServerboundSquadMemberActionPayload.MAKE_LEADER.equals(action)) {
            // Only a commander decides who leads - a leader must not be able to appoint their successor.
            if (!RoleService.isAdmin(player) && !RoleService.isCommander(server, player)) {
                return;
            }
            SquadService.setLeader(server, payload.squadId(), payload.target());
            sendSquadListToTeam(server, TeamService.getTeam(server, player));
        } else if (ServerboundSquadMemberActionPayload.KICK.equals(action)) {
            if (player.getUUID().equals(payload.target())) {
                return;   // leaving is what the Leave button is for
            }
            String oldSquad = SquadService.kick(server, payload.target());
            if (oldSquad == null) {
                return;
            }
            ServerPlayer kicked = server.getPlayerList().getPlayer(payload.target());
            if (kicked != null) {
                sendSquadState(kicked);
                KitNetworking.sendCatalogFor(kicked);
            }
            sendSquadListToTeam(server, TeamService.getTeam(server, player));
            refreshCatalogForSquad(server, oldSquad);
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

    /**
     * Filled in only for someone who may edit this squad - everyone else gets {@code NONE}, so a
     * roster and a team's reservations never reach a client with no business reading them.
     */
    private static SquadManageInfo manageInfoFor(ServerPlayer viewer, WarState.SquadRecord record) {
        MinecraftServer server = viewer.server;
        if (!RoleService.canEditSquad(server, viewer, record.id)) {
            return SquadManageInfo.NONE;
        }
        WarState st = WarState.get(server);
        List<SquadMember> members = new ArrayList<>();
        String leaderName = "";
        for (java.util.UUID id : st.squadMembers(record.id)) {
            boolean isLeader = id.equals(record.leader);
            String name = nameOf(server, id);
            if (isLeader) {
                leaderName = name;
            }
            members.add(new SquadMember(name, id, isLeader));
        }
        List<KitCountEntry> reservations = new ArrayList<>();
        TeamKits.budgetsOf(record.team).forEach((kitId, total) -> KitStorage.get(kitId).ifPresent(kit ->
                reservations.add(new KitCountEntry(kitId, kit.displayNameOr(kitId),
                        record.kitReservations.getOrDefault(kitId, 0)))));
        return new SquadManageInfo(true, leaderName, members, reservations);
    }

    private static String nameOf(MinecraftServer server, java.util.UUID id) {
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        if (online != null) {
            return online.getGameProfile().getName();
        }
        return server.getProfileCache() == null ? id.toString()
                : server.getProfileCache().get(id).map(p -> p.getName()).orElse(id.toString());
    }

    public static void sendSquadListFor(ServerPlayer player) {
        String team = TeamService.getTeam(player.server, player);
        List<SquadEntry> entries = new ArrayList<>();
        if (team != null) {
            for (WarState.SquadRecord record : SquadService.squadsFor(player.server, team)) {
                entries.add(new SquadEntry(record.id, record.name,
                        SquadService.memberCount(player.server, record.id), record.limit,
                        manageInfoFor(player, record)));
            }
        }
        PacketDistributor.sendToPlayer(player, new ClientboundSquadListPayload(entries));
    }

    public static void sendSquadState(ServerPlayer player) {
        String squadId = SquadService.getSquadId(player.server, player);
        PacketDistributor.sendToPlayer(player, new ClientboundSquadStatePayload(
                squadId == null ? "" : squadId, RoleService.canCreateSquad(player.server, player),
                RoleService.isCommander(player.server, player)));
    }
}
