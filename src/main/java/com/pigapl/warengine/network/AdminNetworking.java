package com.pigapl.warengine.network;

import com.pigapl.warengine.WarConfig;
import com.pigapl.warengine.WarEngine;
import com.pigapl.warengine.base.BaseService;
import com.pigapl.warengine.kit.KitDefinition;
import com.pigapl.warengine.kit.KitService;
import com.pigapl.warengine.kit.KitStorage;
import com.pigapl.warengine.kit.ScarceItems;
import com.pigapl.warengine.kit.TeamKits;
import com.pigapl.warengine.network.client.ClientPayloadHandlers;
import com.pigapl.warengine.round.RoundService;
import com.pigapl.warengine.squad.SquadService;
import com.pigapl.warengine.state.WarState;
import com.pigapl.warengine.team.TeamService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.scores.PlayerTeam;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Registers and handles every admin-panel payload.
 *
 * <p><b>Every inbound handler re-checks op status itself.</b> A raw payload handler gets NO automatic
 * permission gate the way Brigadier's {@code .requires(...)} does - anyone who can connect can send
 * any registered payload, and a modified client never has to run {@code /warstate admin} first.
 * {@link #opPlayerOrNull} is that check.</p>
 */
public final class AdminNetworking {
    private AdminNetworking() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        // "2": admin player rows gained squadId, so both panels can offer the same actions.
        PayloadRegistrar registrar = event.registrar("2");

        registrar.playToServer(ServerboundRequestAdminSnapshotPayload.TYPE,
                ServerboundRequestAdminSnapshotPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleRequestSnapshot(context)));
        registrar.playToServer(ServerboundAdminStartWarPayload.TYPE, ServerboundAdminStartWarPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleStartWar(payload, context)));
        registrar.playToServer(ServerboundAdminEndWarPayload.TYPE, ServerboundAdminEndWarPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleEndWar(context)));
        registrar.playToServer(ServerboundAdminAdjustTimePayload.TYPE, ServerboundAdminAdjustTimePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleAdjustTime(payload, context)));
        registrar.playToServer(ServerboundAdminSetTicketCapPayload.TYPE, ServerboundAdminSetTicketCapPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleSetTicketCap(payload, context)));
        registrar.playToServer(ServerboundAdminResetTicketsPayload.TYPE, ServerboundAdminResetTicketsPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleResetTickets(context)));
        registrar.playToServer(ServerboundAdminTeleportToPointPayload.TYPE, ServerboundAdminTeleportToPointPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleTeleportToPoint(payload, context)));

        registrar.playToServer(ServerboundRequestAdminTeamsSnapshotPayload.TYPE,
                ServerboundRequestAdminTeamsSnapshotPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleRequestTeamsSnapshot(context)));
        registrar.playToServer(ServerboundAdminKickSquadMemberPayload.TYPE,
                ServerboundAdminKickSquadMemberPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleKickSquadMember(payload, context)));
        registrar.playToServer(ServerboundAdminUpsertTeamPayload.TYPE, ServerboundAdminUpsertTeamPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleUpsertTeam(payload, context)));
        registrar.playToServer(ServerboundAdminDeleteTeamPayload.TYPE, ServerboundAdminDeleteTeamPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleDeleteTeam(payload, context)));
        registrar.playToServer(ServerboundAdminRestoreTeamsPayload.TYPE, ServerboundAdminRestoreTeamsPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleRestoreTeams(context)));
        registrar.playToServer(ServerboundAdminMoveToTeamPayload.TYPE, ServerboundAdminMoveToTeamPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleMoveToTeam(payload, context)));
        registrar.playToServer(ServerboundAdminRenameSquadPayload.TYPE, ServerboundAdminRenameSquadPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleRenameSquad(payload, context)));
        registrar.playToServer(ServerboundAdminSetSquadLimitPayload.TYPE, ServerboundAdminSetSquadLimitPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleSetSquadLimit(payload, context)));
        registrar.playToServer(ServerboundAdminMoveToSquadPayload.TYPE, ServerboundAdminMoveToSquadPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleMoveToSquad(payload, context)));

        registrar.playToServer(ServerboundAdminSetTeamBasePayload.TYPE, ServerboundAdminSetTeamBasePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleSetTeamBase(payload, context)));
        registrar.playToServer(ServerboundAdminAdjustBaseRadiusPayload.TYPE,
                ServerboundAdminAdjustBaseRadiusPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleAdjustBaseRadius(payload, context)));
        registrar.playToServer(ServerboundAdminClearTeamBasePayload.TYPE,
                ServerboundAdminClearTeamBasePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleClearTeamBase(payload, context)));
        registrar.playToServer(ServerboundAdminTeleportToTeamBasePayload.TYPE,
                ServerboundAdminTeleportToTeamBasePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleTeleportToTeamBase(payload, context)));
        registrar.playToServer(ServerboundAdminTeleportAllToBasesPayload.TYPE,
                ServerboundAdminTeleportAllToBasesPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleTeleportAllToBases(context)));
        registrar.playToServer(ServerboundAdminSendToBasePayload.TYPE, ServerboundAdminSendToBasePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleSendToBase(payload, context)));

        registrar.playToServer(ServerboundAdminTeleportToPlayerPayload.TYPE, ServerboundAdminTeleportToPlayerPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleTeleportToPlayer(payload, context)));
        registrar.playToServer(ServerboundAdminForceKitPayload.TYPE, ServerboundAdminForceKitPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleForceKit(payload, context)));
        registrar.playToServer(ServerboundAdminForceResupplyPayload.TYPE, ServerboundAdminForceResupplyPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleForceResupply(payload, context)));

        registrar.playToServer(ServerboundRequestAdminKitsSnapshotPayload.TYPE,
                ServerboundRequestAdminKitsSnapshotPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleRequestKitsSnapshot(context)));
        registrar.playToServer(ServerboundAdminUpdateKitPayload.TYPE, ServerboundAdminUpdateKitPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleUpdateKit(payload, context)));
        registrar.playToServer(ServerboundAdminToggleKitTeamPayload.TYPE, ServerboundAdminToggleKitTeamPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleToggleKitTeam(payload, context)));
        registrar.playToServer(ServerboundAdminDeleteKitPayload.TYPE, ServerboundAdminDeleteKitPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleDeleteKit(payload, context)));

        registrar.playToServer(ServerboundRequestAdminBudgetSnapshotPayload.TYPE,
                ServerboundRequestAdminBudgetSnapshotPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleRequestBudgetSnapshot(context)));
        registrar.playToServer(ServerboundAdminSetBudgetPayload.TYPE, ServerboundAdminSetBudgetPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleSetBudget(payload, context)));

        registrar.playToServer(ServerboundRequestAdminScarceSnapshotPayload.TYPE,
                ServerboundRequestAdminScarceSnapshotPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleRequestScarceSnapshot(context)));
        registrar.playToServer(ServerboundAdminMarkHeldScarcePayload.TYPE, ServerboundAdminMarkHeldScarcePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleMarkHeldScarce(context)));
        registrar.playToServer(ServerboundAdminUnmarkScarceAtPayload.TYPE, ServerboundAdminUnmarkScarceAtPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleUnmarkScarceAt(payload, context)));
        registrar.playToServer(ServerboundAdminBulkScarcePayload.TYPE, ServerboundAdminBulkScarcePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleBulkScarce(payload, context)));
        registrar.playToServer(ServerboundAdminToggleExemptPayload.TYPE,
                ServerboundAdminToggleExemptPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleToggleExempt(payload, context)));
        registrar.playToServer(ServerboundAdminToggleKeepInBasePayload.TYPE,
                ServerboundAdminToggleKeepInBasePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleToggleKeepInBase(context)));
        registrar.playToServer(ServerboundAdminToggleRolePayload.TYPE,
                ServerboundAdminToggleRolePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleToggleRole(payload, context)));

        // ---- outbound (S2C): must be registered on BOTH dists even on a dedicated server. The dist
        // guard goes inside the handler body, never around the registration. ----
        registrar.playToClient(ClientboundAdminSnapshotPayload.TYPE, ClientboundAdminSnapshotPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (FMLEnvironment.dist.isClient()) {
                        context.enqueueWork(() -> ClientPayloadHandlers.handleAdminSnapshot(payload, context));
                    }
                });
        registrar.playToClient(ClientboundOpenAdminScreenPayload.TYPE, ClientboundOpenAdminScreenPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (FMLEnvironment.dist.isClient()) {
                        context.enqueueWork(() -> ClientPayloadHandlers.handleOpenAdminScreen(context));
                    }
                });
        registrar.playToClient(ClientboundAdminTeamsSnapshotPayload.TYPE, ClientboundAdminTeamsSnapshotPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (FMLEnvironment.dist.isClient()) {
                        context.enqueueWork(() -> ClientPayloadHandlers.handleAdminTeamsSnapshot(payload, context));
                    }
                });
        registrar.playToClient(ClientboundAdminKitsSnapshotPayload.TYPE, ClientboundAdminKitsSnapshotPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (FMLEnvironment.dist.isClient()) {
                        context.enqueueWork(() -> ClientPayloadHandlers.handleAdminKitsSnapshot(payload, context));
                    }
                });
        registrar.playToClient(ClientboundAdminScarceSnapshotPayload.TYPE, ClientboundAdminScarceSnapshotPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (FMLEnvironment.dist.isClient()) {
                        context.enqueueWork(() -> ClientPayloadHandlers.handleAdminScarceSnapshot(payload, context));
                    }
                });
        registrar.playToClient(ClientboundAdminBudgetSnapshotPayload.TYPE, ClientboundAdminBudgetSnapshotPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (FMLEnvironment.dist.isClient()) {
                        context.enqueueWork(() -> ClientPayloadHandlers.handleAdminBudgetSnapshot(payload, context));
                    }
                });
    }


    /** @return the sender, but only if they are both a real player AND currently op - else {@code null}. */
    private static ServerPlayer opPlayerOrNull(IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return null;
        }
        return player.createCommandSourceStack().hasPermission(2) ? player : null;
    }


    private static void handleRequestSnapshot(IPayloadContext context) {
        ServerPlayer player = opPlayerOrNull(context);
        if (player == null) {
            return;
        }
        PacketDistributor.sendToPlayer(player, buildSnapshot(player.server));
    }

    private static void handleStartWar(ServerboundAdminStartWarPayload payload, IPayloadContext context) {
        ServerPlayer player = opPlayerOrNull(context);
        if (player == null) {
            return;
        }
        MinecraftServer server = player.server;
        if (!WarState.get(server).roundActive()) {
            // Clamp defensively - a raw payload value skips Brigadier's IntegerArgumentType bounds.
            int minutes = Math.max(1, Math.min(240, payload.minutes()));
            RoundService.start(server, minutes);
        }
        PacketDistributor.sendToPlayer(player, buildSnapshot(server));
    }

    private static void handleEndWar(IPayloadContext context) {
        ServerPlayer player = opPlayerOrNull(context);
        if (player == null) {
            return;
        }
        RoundService.end(player.server, "ENDED BY ADMIN");
        PacketDistributor.sendToPlayer(player, buildSnapshot(player.server));
    }

    private static void handleAdjustTime(ServerboundAdminAdjustTimePayload payload, IPayloadContext context) {
        ServerPlayer player = opPlayerOrNull(context);
        if (player == null) {
            return;
        }
        int delta = Math.max(-240, Math.min(240, payload.deltaMinutes()));
        WarState.get(player.server).adjustRoundEnd(delta * 60_000L);
        PacketDistributor.sendToPlayer(player, buildSnapshot(player.server));
    }

    private static void handleSetTicketCap(ServerboundAdminSetTicketCapPayload payload, IPayloadContext context) {
        ServerPlayer player = opPlayerOrNull(context);
        if (player == null) {
            return;
        }
        int cap = Math.max(1, Math.min(1_000_000, payload.cap()));
        WarEngine.LOGGER.info("[war] {} set ticket cap {} -> {}", player.getGameProfile().getName(),
                WarConfig.ROUND_TICKET_CAP.get(), cap);
        WarConfig.ROUND_TICKET_CAP.set(cap);
        PacketDistributor.sendToPlayer(player, buildSnapshot(player.server));
    }

    private static void handleResetTickets(IPayloadContext context) {
        ServerPlayer player = opPlayerOrNull(context);
        if (player == null) {
            return;
        }
        WarState st = WarState.get(player.server);
        for (String team : new ArrayList<>(st.tickets().keySet())) {
            st.setTickets(team, 0);
        }
        PacketDistributor.sendToPlayer(player, buildSnapshot(player.server));
    }

    private static void handleTeleportToPoint(ServerboundAdminTeleportToPointPayload payload,
                                              IPayloadContext context) {
        ServerPlayer player = opPlayerOrNull(context);
        if (player == null) {
            return;
        }
        WarState.CapturePoint point = WarState.get(player.server).getPoint(payload.pointId());
        if (point == null) {
            return;
        }
        ServerLevel level = RoundService.levelOf(player.server, point);
        if (level == null) {
            return;
        }
        player.teleportTo(level, point.x + 0.5, point.y + 1.0, point.z + 0.5,
                Set.of(), player.getYRot(), player.getXRot());
    }


    private static void handleRequestTeamsSnapshot(IPayloadContext context) {
        ServerPlayer player = opPlayerOrNull(context);
        if (player == null) {
            return;
        }
        PacketDistributor.sendToPlayer(player, buildTeamsSnapshot(player.server));
    }

    /** Works on an offline target - a roster tracks by id, not presence. */
    private static void handleKickSquadMember(ServerboundAdminKickSquadMemberPayload payload, IPayloadContext context) {
        ServerPlayer admin = opPlayerOrNull(context);
        if (admin == null) {
            return;
        }
        MinecraftServer server = admin.server;
        UUID target = payload.target();
        WarState.SquadRecord before = SquadService.getSquad(server, SquadService.getSquadId(server, target));
        String oldSquad = SquadService.kick(server, target);
        if (oldSquad == null) {
            return;
        }
        ServerPlayer targetOnline = server.getPlayerList().getPlayer(target);
        if (targetOnline != null) {
            SquadNetworking.sendSquadState(targetOnline);
        }
        if (before != null) {
            SquadNetworking.sendSquadListToTeam(server, before.team);
        }
        KitNetworking.sendCatalogToSquad(server, oldSquad);
        PacketDistributor.sendToPlayer(admin, buildTeamsSnapshot(server));
    }

    private static void handleUpsertTeam(ServerboundAdminUpsertTeamPayload payload, IPayloadContext context) {
        ServerPlayer admin = opPlayerOrNull(context);
        if (admin == null) {
            return;
        }
        try {
            TeamKits.addTeam(admin.server, payload.id(), payload.color().isEmpty() ? null : payload.color());
        } catch (IOException e) {
            WarEngine.LOGGER.error("[admin] upsert team '{}' failed", payload.id(), e);
        }
        PacketDistributor.sendToPlayer(admin, buildTeamsSnapshot(admin.server));
    }

    private static void handleDeleteTeam(ServerboundAdminDeleteTeamPayload payload, IPayloadContext context) {
        ServerPlayer admin = opPlayerOrNull(context);
        if (admin == null) {
            return;
        }
        try {
            TeamKits.removeTeam(admin.server, payload.id());
        } catch (IOException e) {
            WarEngine.LOGGER.error("[admin] delete team '{}' failed", payload.id(), e);
        }
        PacketDistributor.sendToPlayer(admin, buildTeamsSnapshot(admin.server));
    }

    private static void handleRestoreTeams(IPayloadContext context) {
        ServerPlayer admin = opPlayerOrNull(context);
        if (admin == null) {
            return;
        }
        TeamKits.restore(admin.server);
        PacketDistributor.sendToPlayer(admin, buildTeamsSnapshot(admin.server));
    }

    private static void handleMoveToTeam(ServerboundAdminMoveToTeamPayload payload, IPayloadContext context) {
        ServerPlayer admin = opPlayerOrNull(context);
        if (admin == null) {
            return;
        }
        MinecraftServer server = admin.server;
        ServerPlayer target = server.getPlayerList().getPlayer(payload.target());
        if (target == null) {
            return;
        }
        // Kit/squad drop and catalog refresh follow within ~1s via GameEvents.onTeamChangeTick.
        TeamService.assign(server, target, payload.team());
        PacketDistributor.sendToPlayer(admin, buildTeamsSnapshot(server));
    }

    private static void handleRenameSquad(ServerboundAdminRenameSquadPayload payload, IPayloadContext context) {
        ServerPlayer admin = opPlayerOrNull(context);
        if (admin == null) {
            return;
        }
        MinecraftServer server = admin.server;
        if (SquadService.rename(server, payload.squadId(), payload.name()) == SquadService.UpdateResult.OK) {
            refreshSquadTeamList(server, payload.squadId());
        }
        PacketDistributor.sendToPlayer(admin, buildTeamsSnapshot(server));
    }

    private static void handleSetSquadLimit(ServerboundAdminSetSquadLimitPayload payload, IPayloadContext context) {
        ServerPlayer admin = opPlayerOrNull(context);
        if (admin == null) {
            return;
        }
        MinecraftServer server = admin.server;
        if (SquadService.setLimit(server, payload.squadId(), payload.limit()) == SquadService.UpdateResult.OK) {
            refreshSquadTeamList(server, payload.squadId());
        }
        PacketDistributor.sendToPlayer(admin, buildTeamsSnapshot(server));
    }

    private static void refreshSquadTeamList(MinecraftServer server, String squadId) {
        WarState.SquadRecord record = SquadService.getSquad(server, squadId);
        if (record != null) {
            SquadNetworking.sendSquadListToTeam(server, record.team);
        }
    }

    private static void handleMoveToSquad(ServerboundAdminMoveToSquadPayload payload, IPayloadContext context) {
        ServerPlayer admin = opPlayerOrNull(context);
        if (admin == null) {
            return;
        }
        MinecraftServer server = admin.server;
        ServerPlayer target = server.getPlayerList().getPlayer(payload.target());
        if (target == null) {
            return;
        }
        String oldSquad = SquadService.getSquadId(server, target);
        if (SquadService.join(target, payload.squadId()) == SquadService.JoinResult.OK) {
            SquadNetworking.refreshAfterSquadChange(target, oldSquad, payload.squadId());
        }
        PacketDistributor.sendToPlayer(admin, buildTeamsSnapshot(server));
    }


    /** Position is read off the sender, never the payload - a modified client cannot pick coordinates. */
    private static void handleSetTeamBase(ServerboundAdminSetTeamBasePayload payload, IPayloadContext context) {
        ServerPlayer admin = opPlayerOrNull(context);
        if (admin == null) {
            return;
        }
        MinecraftServer server = admin.server;
        String team = payload.team();
        if (TeamService.ids(server).stream().noneMatch(id -> id.equalsIgnoreCase(team))) {
            return;
        }
        BaseService.set(server, team, admin);
        admin.sendSystemMessage(Component.literal("Base for team '" + team + "' set to "
                + BaseService.describe(BaseService.of(server, team))).withStyle(ChatFormatting.GREEN));
        PacketDistributor.sendToPlayer(admin, buildTeamsSnapshot(server));
    }

    private static void handleAdjustBaseRadius(ServerboundAdminAdjustBaseRadiusPayload payload,
                                               IPayloadContext context) {
        ServerPlayer admin = opPlayerOrNull(context);
        if (admin == null) {
            return;
        }
        MinecraftServer server = admin.server;
        // Clamped here too - the panel sends 5, but a payload is just bytes.
        int delta = Math.max(-50, Math.min(50, payload.delta()));
        double next = BaseService.adjustRadius(server, payload.team(), delta);
        if (next >= 0.0) {
            WarEngine.LOGGER.info("[admin] {} set base size for '{}' to {}",
                    admin.getGameProfile().getName(), payload.team(), Math.round(next));
        }
        PacketDistributor.sendToPlayer(admin, buildTeamsSnapshot(server));
    }

    private static void handleClearTeamBase(ServerboundAdminClearTeamBasePayload payload, IPayloadContext context) {
        ServerPlayer admin = opPlayerOrNull(context);
        if (admin == null) {
            return;
        }
        if (BaseService.clear(admin.server, payload.team())) {
            admin.sendSystemMessage(Component.literal("Base for team '" + payload.team() + "' cleared - "
                    + "that team can now pick kits anywhere.").withStyle(ChatFormatting.YELLOW));
        }
        PacketDistributor.sendToPlayer(admin, buildTeamsSnapshot(admin.server));
    }

    private static void handleTeleportToTeamBase(ServerboundAdminTeleportToTeamBasePayload payload,
                                                 IPayloadContext context) {
        ServerPlayer admin = opPlayerOrNull(context);
        if (admin == null) {
            return;
        }
        WarState.TeamBase base = BaseService.of(admin.server, payload.team());
        if (base != null) {
            BaseService.teleportTo(admin, base);
        }
    }

    private static void handleTeleportAllToBases(IPayloadContext context) {
        ServerPlayer admin = opPlayerOrNull(context);
        if (admin == null) {
            return;
        }
        int moved = BaseService.teleportAllToBases(admin.server);
        admin.sendSystemMessage(Component.literal("Sent " + moved + " player(s) to their team's base.")
                .withStyle(ChatFormatting.GREEN));
        WarEngine.LOGGER.info("[base] {} teleported {} player(s) to their team bases",
                admin.getGameProfile().getName(), moved);
    }


    private static void handleTeleportToPlayer(ServerboundAdminTeleportToPlayerPayload payload, IPayloadContext context) {
        ServerPlayer admin = opPlayerOrNull(context);
        if (admin == null) {
            return;
        }
        ServerPlayer target = admin.server.getPlayerList().getPlayer(payload.target());
        if (target == null || !(target.level() instanceof ServerLevel level)) {
            return;
        }
        admin.teleportTo(level, target.getX(), target.getY(), target.getZ(),
                Set.of(), target.getYRot(), target.getXRot());
    }

    private static void handleForceKit(ServerboundAdminForceKitPayload payload, IPayloadContext context) {
        ServerPlayer admin = opPlayerOrNull(context);
        if (admin == null) {
            return;
        }
        MinecraftServer server = admin.server;
        ServerPlayer target = server.getPlayerList().getPlayer(payload.target());
        if (target == null) {
            return;
        }
        String norm = KitStorage.normalizeId(payload.kitId());
        KitStorage.get(norm).ifPresent(kit -> {
            String old = WarState.get(server).getKit(target.getUUID());
            KitService.apply(target, kit);
            WarState.get(server).setKit(target.getUUID(), norm);
            WarEngine.LOGGER.info("[kit] {} force-gave '{}' to {} (was '{}')", admin.getGameProfile().getName(),
                    norm, target.getGameProfile().getName(), old);
            KitNetworking.sendKitState(target);
            KitNetworking.sendCatalogToSquad(server, WarState.get(server).getSquad(target.getUUID()));
        });
        PacketDistributor.sendToPlayer(admin, buildTeamsSnapshot(server));
    }

    private static void handleSendToBase(ServerboundAdminSendToBasePayload payload, IPayloadContext context) {
        ServerPlayer admin = opPlayerOrNull(context);
        if (admin == null) {
            return;
        }
        ServerPlayer target = admin.server.getPlayerList().getPlayer(payload.target());
        if (target == null) {
            return;
        }
        String name = target.getGameProfile().getName();
        if (BaseService.teleportToOwnBase(target)) {
            admin.sendSystemMessage(Component.literal("Sent " + name + " to their team's base.")
                    .withStyle(ChatFormatting.GREEN));
            target.sendSystemMessage(Component.literal("An admin sent you back to your team's base.")
                    .withStyle(ChatFormatting.YELLOW));
            WarEngine.LOGGER.info("[base] {} sent {} to their team base", admin.getGameProfile().getName(), name);
        } else {
            admin.sendSystemMessage(Component.literal(name + "'s team has no base set.")
                    .withStyle(ChatFormatting.RED));
        }
    }

    private static void handleForceResupply(ServerboundAdminForceResupplyPayload payload, IPayloadContext context) {
        ServerPlayer admin = opPlayerOrNull(context);
        if (admin == null) {
            return;
        }
        MinecraftServer server = admin.server;
        ServerPlayer target = server.getPlayerList().getPlayer(payload.target());
        if (target == null) {
            return;
        }
        String kitId = WarState.get(server).getKit(target.getUUID());
        if (kitId != null) {
            KitStorage.get(kitId).ifPresent(kit -> KitService.reconcile(target, kit, 0));
        }
    }


    private static void handleRequestKitsSnapshot(IPayloadContext context) {
        ServerPlayer admin = opPlayerOrNull(context);
        if (admin == null) {
            return;
        }
        PacketDistributor.sendToPlayer(admin, buildKitsSnapshot(admin.server));
    }

    private static void handleUpdateKit(ServerboundAdminUpdateKitPayload payload, IPayloadContext context) {
        ServerPlayer admin = opPlayerOrNull(context);
        if (admin == null) {
            return;
        }
        MinecraftServer server = admin.server;
        String norm = KitStorage.normalizeId(payload.kitId());
        KitStorage.get(norm).ifPresent(kit -> {
            KitDefinition updated = kit.withDisplayName(payload.displayName());
            try {
                KitStorage.save(norm, updated, server);
                refreshCatalogsForKit(server, norm);
            } catch (IOException e) {
                WarEngine.LOGGER.error("[admin] update kit '{}' failed", norm, e);
            }
        });
        PacketDistributor.sendToPlayer(admin, buildKitsSnapshot(server));
    }

    private static void handleToggleKitTeam(ServerboundAdminToggleKitTeamPayload payload, IPayloadContext context) {
        ServerPlayer admin = opPlayerOrNull(context);
        if (admin == null) {
            return;
        }
        MinecraftServer server = admin.server;
        String kit = KitStorage.normalizeId(payload.kitId());
        String team = KitStorage.normalizeId(payload.team());
        if (KitStorage.get(kit).isEmpty()) {
            return;
        }
        TeamKits.TeamDef def = TeamKits.all().get(team);
        boolean currentlyAssigned = def != null && def.kits().contains(kit);
        try {
            if (currentlyAssigned) {
                TeamKits.unassign(kit, team);
            } else {
                TeamKits.assign(kit, team);
            }
            refreshCatalogsForKit(server, kit);
        } catch (IOException e) {
            WarEngine.LOGGER.error("[admin] toggle kit '{}' x team '{}' failed", kit, team, e);
        }
        PacketDistributor.sendToPlayer(admin, buildKitsSnapshot(server));
    }

    private static void handleDeleteKit(ServerboundAdminDeleteKitPayload payload, IPayloadContext context) {
        ServerPlayer admin = opPlayerOrNull(context);
        if (admin == null) {
            return;
        }
        MinecraftServer server = admin.server;
        String norm = KitStorage.normalizeId(payload.kitId());
        try {
            if (KitStorage.delete(norm)) {
                TeamKits.removeKitEverywhere(norm);
                for (ServerPlayer online : server.getPlayerList().getPlayers()) {
                    KitNetworking.sendCatalogFor(online);
                }
            }
        } catch (IOException e) {
            WarEngine.LOGGER.error("[admin] delete kit '{}' failed", norm, e);
        }
        PacketDistributor.sendToPlayer(admin, buildKitsSnapshot(server));
    }

    private static void refreshCatalogsForKit(MinecraftServer server, String kitId) {
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            String theirTeam = TeamService.getTeam(server, online);
            if (theirTeam != null && TeamKits.isAllowed(theirTeam, kitId)) {
                KitNetworking.sendCatalogFor(online);
            }
        }
    }


    private static void handleRequestBudgetSnapshot(IPayloadContext context) {
        ServerPlayer admin = opPlayerOrNull(context);
        if (admin == null) {
            return;
        }
        PacketDistributor.sendToPlayer(admin, buildBudgetSnapshot(admin.server));
    }

    private static void handleSetBudget(ServerboundAdminSetBudgetPayload payload, IPayloadContext context) {
        ServerPlayer admin = opPlayerOrNull(context);
        if (admin == null) {
            return;
        }
        MinecraftServer server = admin.server;
        String team = KitStorage.normalizeId(payload.team());
        String kit = KitStorage.normalizeId(payload.kitId());
        if (!team.equals(TeamKits.ALL_TEAMS) && KitStorage.get(kit).isPresent()) {
            int count = Math.max(0, Math.min(100_000, payload.count()));
            try {
                TeamKits.setBudget(team, kit, count);
                refreshCatalogsForKit(server, kit);
                // A budget change also shifts every squad's "remaining" on the Create screen.
                for (ServerPlayer online : server.getPlayerList().getPlayers()) {
                    if (team.equalsIgnoreCase(TeamService.getTeam(server, online))) {
                        KitNetworking.sendKitBudgetFor(online);
                    }
                }
            } catch (IOException e) {
                WarEngine.LOGGER.error("[admin] set budget {} x {} failed", team, kit, e);
            }
        }
        PacketDistributor.sendToPlayer(admin, buildBudgetSnapshot(server));
    }


    private static void handleRequestScarceSnapshot(IPayloadContext context) {
        ServerPlayer admin = opPlayerOrNull(context);
        if (admin == null) {
            return;
        }
        PacketDistributor.sendToPlayer(admin, buildScarceSnapshot());
    }

    private static void handleMarkHeldScarce(IPayloadContext context) {
        ServerPlayer admin = opPlayerOrNull(context);
        if (admin == null) {
            return;
        }
        ItemStack held = admin.getMainHandItem();
        try {
            ScarceItems.add(held, admin.server);
        } catch (IOException e) {
            WarEngine.LOGGER.error("[admin] mark scarce failed", e);
        }
        PacketDistributor.sendToPlayer(admin, buildScarceSnapshot());
    }

    private static void handleUnmarkScarceAt(ServerboundAdminUnmarkScarceAtPayload payload, IPayloadContext context) {
        ServerPlayer admin = opPlayerOrNull(context);
        if (admin == null) {
            return;
        }
        try {
            ScarceItems.removeAt(payload.index(), admin.server);
        } catch (IOException e) {
            WarEngine.LOGGER.error("[admin] unmark scarce failed", e);
        }
        PacketDistributor.sendToPlayer(admin, buildScarceSnapshot());
    }


    public static ClientboundAdminSnapshotPayload buildSnapshot(MinecraftServer server) {
        WarState st = WarState.get(server);
        long timeLeft = st.roundActive() ? Math.max(0L, st.roundEndEpochMillis() - System.currentTimeMillis()) : 0L;

        List<AdminTeamInfo> teams = new ArrayList<>();
        for (String team : TeamService.ids(server)) {
            PlayerTeam live = server.getScoreboard().getPlayerTeam(team);
            List<String> online = new ArrayList<>();
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (team.equalsIgnoreCase(TeamService.getTeam(server, p))) {
                    online.add(p.getGameProfile().getName());
                }
            }
            teams.add(new AdminTeamInfo(team, accentFor(live), st.getTickets(team), online));
        }

        int captureTotal = WarConfig.CAPTURE_SECONDS.get();
        List<AdminPointInfo> points = new ArrayList<>();
        for (WarState.CapturePoint p : st.points()) {
            // Same occupancy rule the scoring uses - never a second implementation that could drift.
            Map<String, List<String>> occupants = RoundService.occupantsByTeam(server, p);
            List<String> inside = occupants.values().stream().flatMap(List::stream).toList();
            points.add(new AdminPointInfo(
                    new AdminPointLoc(p.id, p.dim, p.x, p.y, p.z, p.radius),
                    new AdminPointStatus(p.owner == null ? "" : p.owner,
                            p.capturingTeam == null ? "" : p.capturingTeam,
                            p.progress, captureTotal, occupants.size() >= 2, inside)));
        }

        List<AdminHistoryEntry> history = new ArrayList<>();
        for (WarState.CaptureLogEntry e : st.captureHistory()) {
            history.add(new AdminHistoryEntry(e.epochMillis, e.point == null ? "" : e.point,
                    e.team == null ? "" : e.team, e.players));
        }

        return new ClientboundAdminSnapshotPayload(new AdminEventFlags(st.roundActive(), st.keepInBase()), timeLeft, WarConfig.ROUND_TICKET_CAP.get(),
                teams, points, history);
    }

    /** Teams list ONLINE members; squads list their FULL roster, so an admin can prune stale ones. */
    public static ClientboundAdminTeamsSnapshotPayload buildTeamsSnapshot(MinecraftServer server) {
        WarState st = WarState.get(server);

        List<AdminTeamDetail> teams = new ArrayList<>();
        for (String team : TeamService.ids(server)) {
            PlayerTeam live = server.getScoreboard().getPlayerTeam(team);
            List<AdminPlayerInfo> members = new ArrayList<>();
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (team.equalsIgnoreCase(TeamService.getTeam(server, p))) {
                    String kitId = st.getKit(p.getUUID());
                    String squadId = SquadService.getSquadId(server, p.getUUID());
                    members.add(new AdminPlayerInfo(p.getGameProfile().getName(), p.getUUID(), true,
                            kitId == null ? "" : kitId, farFromBase(p, st), squadId == null ? "" : squadId,
                            st.isExempt(p.getUUID()),
                            team.equalsIgnoreCase(orEmptyRole(st.getCommanderOf(p.getUUID()))),
                            team.equalsIgnoreCase(orEmptyRole(st.getLeaderOf(p.getUUID())))));
                }
            }
            String displayName = live == null ? team : live.getDisplayName().getString();
            WarState.TeamBase base = BaseService.of(server, team);
            teams.add(new AdminTeamDetail(team, accentFor(live), displayName, members,
                    base == null ? "" : BaseService.describe(base) + "  (" + Math.round(BaseService.radiusOf(base))
                            + "m)"));
        }

        List<AdminSquadDetail> squads = new ArrayList<>();
        for (WarState.SquadRecord record : st.squads().values()) {
            List<AdminPlayerInfo> members = new ArrayList<>();
            for (UUID id : st.squadMembers(record.id)) {
                ServerPlayer livePlayer = server.getPlayerList().getPlayer(id);
                String kitId = st.getKit(id);
                members.add(new AdminPlayerInfo(resolveName(server, id), id, livePlayer != null,
                        kitId == null ? "" : kitId, farFromBase(livePlayer, st), record.id,
                        st.isExempt(id),
                        record.team.equalsIgnoreCase(orEmptyRole(st.getCommanderOf(id))),
                        record.team.equalsIgnoreCase(orEmptyRole(st.getLeaderOf(id)))));
            }
            squads.add(new AdminSquadDetail(record.id, record.team, record.name, record.limit, members));
        }

        return new ClientboundAdminTeamsSnapshotPayload(teams, squads);
    }

    /** Pre-war only - mid-war everyone is out, so highlighting them all would just be noise. */
    /** Roles are stored as the team they were granted for, so a null simply never matches a team id. */
    private static String orEmptyRole(String team) {
        return team == null ? "" : team;
    }

    private static int farFromBase(ServerPlayer p, WarState st) {
        if (p == null || st.roundActive() || st.isExempt(p.getUUID())) {
            return -1;
        }
        String team = TeamService.getTeam(p.server, p);
        if (team == null || BaseService.of(p.server, team) == null || BaseService.withinKitRange(p, team)) {
            return -1;
        }
        double d = BaseService.distanceTo(p, team);
        return d < 0 ? 99_999 : (int) Math.round(d); // -1 = other dimension, which is certainly far
    }

    public static ClientboundAdminKitsSnapshotPayload buildKitsSnapshot(MinecraftServer server) {
        List<AdminKitInfo> kits = new ArrayList<>();
        for (String id : KitStorage.ids().stream().sorted().toList()) {
            KitStorage.get(id).ifPresent(kit -> {
                List<String> assigned = new ArrayList<>();
                TeamKits.all().forEach((team, def) -> {
                    if (def.kits().contains(id)) {
                        assigned.add(team);
                    }
                });
                // limit is vestigial - caps are team budgets + squad reservations. Field kept for the codec.
                kits.add(new AdminKitInfo(id, kit.displayNameOr(id), kit.iconOrGuess(), 0, assigned));
            });
        }
        List<String> teamIds = new ArrayList<>();
        teamIds.add(TeamKits.ALL_TEAMS);
        teamIds.addAll(TeamService.ids(server));
        return new ClientboundAdminKitsSnapshotPayload(kits, teamIds);
    }

    public static ClientboundAdminScarceSnapshotPayload buildScarceSnapshot() {
        List<ItemStack> kitItems = new ArrayList<>();
        for (String id : KitStorage.ids()) {
            KitStorage.get(id).ifPresent(kit -> {
                addDistinct(kitItems, kit.armor());
                addDistinct(kitItems, List.of(kit.offhand()));
                addDistinct(kitItems, kit.inventory());
            });
        }
        return new ClientboundAdminScarceSnapshotPayload(List.copyOf(ScarceItems.all()), kitItems);
    }

    /** One entry per distinct weapon, not per stack - a kit lists 3 mags of the same ammo. */
    private static void addDistinct(List<ItemStack> out, List<ItemStack> candidates) {
        for (ItemStack stack : candidates) {
            if (!stack.isEmpty() && !ScarceItems.matchesAny(out, stack)) {
                out.add(stack.copy());
            }
        }
    }

    private static void handleToggleExempt(ServerboundAdminToggleExemptPayload payload, IPayloadContext context) {
        ServerPlayer admin = opPlayerOrNull(context);
        if (admin == null) {
            return;
        }
        MinecraftServer server = admin.server;
        WarState st = WarState.get(server);
        boolean now = !st.isExempt(payload.target());
        st.setExempt(payload.target(), now);
        WarEngine.LOGGER.info("[admin] {} set exempt={} for {}",
                admin.getGameProfile().getName(), now, resolveName(server, payload.target()));
        ServerPlayer target = server.getPlayerList().getPlayer(payload.target());
        if (target != null) {
            target.displayClientMessage(Component.literal(now ? "Admin mode on" : "Admin mode off")
                    .withStyle(ChatFormatting.GRAY), true);
        }
        PacketDistributor.sendToPlayer(admin, buildTeamsSnapshot(server));
    }

    private static void handleToggleRole(ServerboundAdminToggleRolePayload payload, IPayloadContext context) {
        ServerPlayer admin = opPlayerOrNull(context);
        if (admin == null) {
            return;
        }
        MinecraftServer server = admin.server;
        ServerPlayer target = server.getPlayerList().getPlayer(payload.target());
        // The role is stamped with a team, so it needs the player online to know which team that is.
        String team = target == null ? null : TeamService.getTeam(server, target);
        if (team == null) {
            admin.displayClientMessage(Component.literal("That player needs a team first")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        WarState st = WarState.get(server);
        boolean commander = ServerboundAdminToggleRolePayload.COMMANDER.equals(payload.role());
        boolean leader = ServerboundAdminToggleRolePayload.LEADER.equals(payload.role());
        if (!commander && !leader) {
            return;
        }
        String current = commander ? st.getCommanderOf(payload.target()) : st.getLeaderOf(payload.target());
        String next = team.equalsIgnoreCase(current == null ? "" : current) ? null : team;
        if (commander) {
            st.setCommanderOf(payload.target(), next);
        } else {
            st.setLeaderOf(payload.target(), next);
        }
        WarEngine.LOGGER.info("[admin] {} set {}={} for {} on team {}",
                admin.getGameProfile().getName(), payload.role(), next != null,
                target.getGameProfile().getName(), team);
        target.displayClientMessage(Component.literal(
                        (commander ? "Commander" : "Squad leader") + (next != null ? " ON" : " OFF"))
                .withStyle(next != null ? ChatFormatting.GOLD : ChatFormatting.GRAY), true);
        // The role decides canCreate/canEdit, both of which ride on the squad payloads - without these
        // the target's Create button never ungreys and the Manage button never appears.
        SquadNetworking.sendSquadState(target);
        SquadNetworking.sendSquadListToTeam(server, team);
        PacketDistributor.sendToPlayer(admin, buildTeamsSnapshot(server));
    }

    private static void handleToggleKeepInBase(IPayloadContext context) {
        ServerPlayer admin = opPlayerOrNull(context);
        if (admin == null) {
            return;
        }
        MinecraftServer server = admin.server;
        WarState st = WarState.get(server);
        boolean now = !st.keepInBase();
        st.setKeepInBase(now);
        WarEngine.LOGGER.info("[admin] {} turned the base lock {}", admin.getGameProfile().getName(),
                now ? "ON" : "OFF");
        server.getPlayerList().broadcastSystemMessage(Component.literal(now ? "Base lock ON" : "Base lock OFF")
                .withStyle(now ? ChatFormatting.RED : ChatFormatting.GRAY), false);
        PacketDistributor.sendToPlayer(admin, buildSnapshot(server));
    }

    private static void handleBulkScarce(ServerboundAdminBulkScarcePayload payload, IPayloadContext context) {
        ServerPlayer admin = opPlayerOrNull(context);
        if (admin == null) {
            return;
        }
        try {
            int changed = ScarceItems.applyBulk(payload.scarce(), payload.notScarce(), admin.server);
            WarEngine.LOGGER.info("[admin] {} bulk-edited the scarce list: {} change(s), now {} item(s)",
                    admin.getGameProfile().getName(), changed, ScarceItems.all().size());
        } catch (IOException e) {
            WarEngine.LOGGER.error("[admin] bulk scarce edit failed", e);
        }
        PacketDistributor.sendToPlayer(admin, buildScarceSnapshot());
    }

    public static ClientboundAdminBudgetSnapshotPayload buildBudgetSnapshot(MinecraftServer server) {
        List<AdminBudgetRow> rows = new ArrayList<>();
        for (String team : TeamService.ids(server)) {
            LinkedHashSet<String> kitIds = new LinkedHashSet<>(TeamKits.kitsFor(team));
            kitIds.addAll(TeamKits.budgetsOf(team).keySet());
            for (String kitId : kitIds) {
                KitStorage.get(kitId).ifPresent(kit -> rows.add(new AdminBudgetRow(
                        team, kitId, kit.displayNameOr(kitId), kit.iconOrGuess(),
                        TeamKits.budgetFor(team, kitId),
                        KitService.reservedByTeam(server, team, kitId))));
            }
        }
        return new ClientboundAdminBudgetSnapshotPayload(rows);
    }

    /** Falls back to a truncated id - not worth losing the ability to kick someone over a cache miss. */
    private static String resolveName(MinecraftServer server, UUID id) {
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        if (online != null) {
            return online.getGameProfile().getName();
        }
        GameProfileCache cache = server.getProfileCache();
        if (cache != null) {
            var profile = cache.get(id);
            if (profile.isPresent()) {
                return profile.get().getName();
            }
        }
        return id.toString().substring(0, 8);
    }

    private static final int DEFAULT_ACCENT = 0xFF8A8A96;

    private static int accentFor(PlayerTeam team) {
        if (team == null) {
            return DEFAULT_ACCENT;
        }
        ChatFormatting color = team.getColor();
        Integer rgb = color == null ? null : color.getColor();
        return rgb == null ? DEFAULT_ACCENT : 0xFF000000 | rgb;
    }
}
