package com.pigapl.warengine.base;

import com.pigapl.warengine.WarConfig;
import com.pigapl.warengine.state.WarState;
import com.pigapl.warengine.team.TeamService;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;

/**
 * Team bases: one saved position per team, driving respawn, "TP all to bases", and where a player may
 * pick a kit ({@link #withinKitRange}). A team with no base is completely unrestricted - that is the
 * feature's off switch. Stored in {@link WarState} (per-world, like the capture zone), not the
 * per-server config: last map's coordinates are meaningless on a fresh one.
 */
public final class BaseService {

    private BaseService() {}

    /** {@code null} if the team has no base (or is {@code null} itself). */
    public static WarState.TeamBase of(MinecraftServer server, String team) {
        return team == null ? null : WarState.get(server).getBase(team);
    }

    /** Records {@code team}'s base at {@code at}'s current position and facing. */
    public static void set(MinecraftServer server, String team, ServerPlayer at) {
        WarState.get(server).setBase(team, at.level().dimension().location().toString(),
                at.getX(), at.getY(), at.getZ(), at.getYRot());
    }

    public static boolean clear(MinecraftServer server, String team) {
        return WarState.get(server).clearBase(team);
    }

    /**
     * Whether {@code player} is close enough to {@code team}'s base to pick a kit. True when the team
     * has no base, or the radius is configured to 0 - the restriction is opt-in by setting a base.
     */
    public static boolean withinKitRange(ServerPlayer player, String team) {
        WarState.TeamBase base = of(player.server, team);
        if (base == null) {
            return true;
        }
        double radius = WarConfig.BASE_KIT_RADIUS.get();
        if (radius <= 0.0) {
            return true;
        }
        if (!player.level().dimension().location().toString().equals(base.dim)) {
            return false;
        }
        return player.distanceToSqr(base.x, base.y, base.z) <= radius * radius;
    }

    /** Blocks from {@code player} to their team's base, or {@code -1} if there is none / another dimension. */
    public static double distanceTo(ServerPlayer player, String team) {
        WarState.TeamBase base = of(player.server, team);
        if (base == null || !player.level().dimension().location().toString().equals(base.dim)) {
            return -1.0;
        }
        return Math.sqrt(player.distanceToSqr(base.x, base.y, base.z));
    }

    /** @return false if the base's dimension no longer exists in this world. */
    public static boolean teleportTo(ServerPlayer player, WarState.TeamBase base) {
        ServerLevel level = levelOf(player.server, base);
        if (level == null) {
            return false;
        }
        player.teleportTo(level, base.x, base.y, base.z, Set.of(), base.yaw, 0.0F);
        return true;
    }

    /** Teleports a player to their OWN team's base. No-op (false) with no team or no base. */
    public static boolean teleportToOwnBase(ServerPlayer player) {
        WarState.TeamBase base = of(player.server, TeamService.getTeam(player.server, player));
        return base != null && teleportTo(player, base);
    }

    /**
     * Sends every online player to their own team's base - the admin panel's "TP All to Bases".
     * Players with no team, or on a team with no base, simply stay where they are.
     *
     * @return how many were actually moved
     */
    public static int teleportAllToBases(MinecraftServer server) {
        int moved = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (teleportToOwnBase(player)) {
                moved++;
            }
        }
        return moved;
    }

    public static ServerLevel levelOf(MinecraftServer server, WarState.TeamBase base) {
        ResourceLocation id = ResourceLocation.tryParse(base.dim);
        return id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
    }

    /** "1234 68 -55" - the short form shown in the admin panel and {@code /warstate base list}. */
    public static String describe(WarState.TeamBase base) {
        return Math.round(base.x) + " " + Math.round(base.y) + " " + Math.round(base.z);
    }
}
