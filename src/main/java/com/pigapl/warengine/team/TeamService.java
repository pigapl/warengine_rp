package com.pigapl.warengine.team;

import net.minecraft.ChatFormatting;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.List;

/**
 * Team lookup/assignment on top of vanilla scoreboard teams - not a custom store. Membership
 * deliberately does NOT live in {@code WarState}: vanilla teams already give colour, friendly-fire
 * and nametags, and already persist. No teams exist until an admin creates one - no default red/blue.
 *
 * <p>The one place to ask "what team is this player on", so callers never touch {@link Scoreboard}
 * directly.</p>
 */
public final class TeamService {

    private TeamService() {}

    public static List<String> ids(MinecraftServer server) {
        return server.getScoreboard().getPlayerTeams().stream()
                .map(PlayerTeam::getName)
                .sorted()
                .toList();
    }

    public static String getTeam(MinecraftServer server, ServerPlayer player) {
        PlayerTeam team = server.getScoreboard().getPlayersTeam(player.getScoreboardName());
        return team == null ? null : team.getName();
    }

    /** One colour table: particle rings tint the same way nametags and team cards already do. */
    public static int colorOf(MinecraftServer server, String teamId, int fallback) {
        if (teamId == null) {
            return fallback;
        }
        PlayerTeam team = server.getScoreboard().getPlayerTeam(teamId);
        ChatFormatting color = team == null ? null : team.getColor();
        Integer rgb = color == null ? null : color.getColor();
        return rgb == null ? fallback : rgb;
    }

    public enum AssignResult { OK, UNKNOWN_TEAM }

    public static AssignResult assign(MinecraftServer server, ServerPlayer player, String teamId) {
        Scoreboard scoreboard = server.getScoreboard();
        PlayerTeam team = scoreboard.getPlayerTeam(teamId);
        if (team == null) {
            return AssignResult.UNKNOWN_TEAM;
        }
        scoreboard.addPlayerToTeam(player.getScoreboardName(), team);
        return AssignResult.OK;
    }
}
