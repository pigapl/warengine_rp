package com.pigapl.warengine.base;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
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
 * One saved position per team, driving respawn, TP-all, and where a kit may be picked. A team with no
 * base is completely unrestricted - that is the off switch. Per-WORLD, in {@link WarState}: last
 * map's coordinates are meaningless on a fresh one.
 */
public final class BaseService {

    private BaseService() {}

    public static WarState.TeamBase of(MinecraftServer server, String team) {
        return team == null ? null : WarState.get(server).getBase(team);
    }

    public static void set(MinecraftServer server, String team, ServerPlayer at) {
        WarState.get(server).setBase(team, at.level().dimension().location().toString(),
                at.getX(), at.getY(), at.getZ(), at.getYRot());
    }

    public static boolean clear(MinecraftServer server, String team) {
        return WarState.get(server).clearBase(team);
    }

    public static final double MIN_RADIUS = 5.0;
    public static final double MAX_RADIUS = 500.0;

    /** The ONE place a base's size is decided: its own radius, else bases.kitRadius. */
    public static double radiusOf(WarState.TeamBase base) {
        return base.radius > 0.0 ? base.radius : WarConfig.BASE_KIT_RADIUS.get();
    }

    /** For messages: the size of this team's base, or the default when it has none. */
    public static double radiusFor(MinecraftServer server, String team) {
        WarState.TeamBase base = of(server, team);
        return base == null ? WarConfig.BASE_KIT_RADIUS.get() : radiusOf(base);
    }

    /** Returns the new radius, or -1 if the team has no base to size. */
    public static double adjustRadius(MinecraftServer server, String team, int delta) {
        WarState.TeamBase base = of(server, team);
        if (base == null) {
            return -1.0;
        }
        double next = Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, radiusOf(base) + delta));
        WarState.get(server).setBaseRadius(team, next);
        return next;
    }

    /** True when the team has no base or the radius is 0 - the restriction is opt-in. */
    public static boolean withinKitRange(ServerPlayer player, String team) {
        // An exempt admin is never out of range: they are running the event from wherever they are.
        if (WarState.get(player.server).isExempt(player.getUUID())) {
            return true;
        }
        WarState.TeamBase base = of(player.server, team);
        if (base == null) {
            return true;
        }
        double radius = radiusOf(base);
        if (radius <= 0.0) {
            return true;
        }
        if (!player.level().dimension().location().toString().equals(base.dim)) {
            return false;
        }
        return player.distanceToSqr(base.x, base.y, base.z) <= radius * radius;
    }

    public static double distanceTo(ServerPlayer player, String team) {
        WarState.TeamBase base = of(player.server, team);
        if (base == null || !player.level().dimension().location().toString().equals(base.dim)) {
            return -1.0;
        }
        return Math.sqrt(player.distanceToSqr(base.x, base.y, base.z));
    }

    public static boolean teleportTo(ServerPlayer player, WarState.TeamBase base) {
        ServerLevel level = levelOf(player.server, base);
        if (level == null) {
            return false;
        }
        player.teleportTo(level, base.x, base.y, base.z, Set.of(), base.yaw, 0.0F);
        return true;
    }

    public static boolean teleportToOwnBase(ServerPlayer player) {
        WarState.TeamBase base = of(player.server, TeamService.getTeam(player.server, player));
        return base != null && teleportTo(player, base);
    }

    private static final Map<UUID, Integer> OUT_OF_BASE = new HashMap<>();

    /**
     * Pre-war base lock, an admin toggle that is off by default. Once a second, anyone outside
     * their base's radius (see radiusOf) gets a countdown on the action bar, then is teleported home. Countdown rather
     * than push-back: pushing does nothing to a player standing on a Sable ship.
     * Skipped: during a war, admin-mode (exempt) players, spectators, and teams with no base.
     */
    public static void keepInBaseTick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) {
            return;
        }
        WarState st = WarState.get(server);
        if (!st.keepInBase() || st.roundActive()) {
            OUT_OF_BASE.clear();
            return;
        }
        int grace = WarConfig.BASE_KEEP_GRACE_SECONDS.get();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID id = player.getUUID();
            String team = TeamService.getTeam(server, player);
            WarState.TeamBase base = team == null ? null : of(server, team);
            if (base == null || st.isExempt(id) || player.isSpectator()) {
                OUT_OF_BASE.remove(id);
                continue;
            }
            double radius = radiusOf(base);
            double dist = distanceTo(player, team);   // -1 = other dimension, which is certainly out
            if (radius <= 0.0 || (dist >= 0.0 && dist <= radius)) {
                OUT_OF_BASE.remove(id);
                continue;
            }
            int left = OUT_OF_BASE.getOrDefault(id, grace + 1) - 1;
            if (left <= 0) {
                OUT_OF_BASE.remove(id);
                player.stopRiding();
                teleportTo(player, base);
                continue;
            }
            OUT_OF_BASE.put(id, left);
            // One word and a number - most players here do not read English.
            player.displayClientMessage(Component.literal("BASE " + left)
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
        }
    }

    /** Players with no team, or a team with no base, stay where they are. */
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

    public static String describe(WarState.TeamBase base) {
        return Math.round(base.x) + " " + Math.round(base.y) + " " + Math.round(base.z);
    }
}
