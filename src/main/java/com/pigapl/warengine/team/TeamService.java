package com.pigapl.warengine.team;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.List;

/**
 * Team lookup/assignment on top of vanilla scoreboard teams - not a custom store.
 *
 * <p>Membership deliberately does NOT live in {@code WarState}: vanilla {@link PlayerTeam}s already
 * give color, friendly-fire, nametag visibility and prefix/suffix through {@code /team}, and already
 * persist across rejoin. No teams exist until an admin creates one - there is no default red/blue.</p>
 *
 * <p>This class is just the one place to ask "what team is this player on" / "put them on team X",
 * so callers never reach into {@link Scoreboard}/{@link PlayerTeam} directly. {@link #assign} backs
 * the SelectTeam payload; a player joining themselves can use {@code /team join <id>}.</p>
 */
public final class TeamService {

    private TeamService() {}

    /** Currently existing team ids (empty until an admin runs /team add). */
    public static List<String> ids(MinecraftServer server) {
        return server.getScoreboard().getPlayerTeams().stream()
                .map(PlayerTeam::getName)
                .sorted()
                .toList();
    }

    /** The team a player is currently on, or {@code null}. */
    public static String getTeam(MinecraftServer server, ServerPlayer player) {
        PlayerTeam team = server.getScoreboard().getPlayersTeam(player.getScoreboardName());
        return team == null ? null : team.getName();
    }

    public enum AssignResult { OK, UNKNOWN_TEAM }

    /** Joins {@code player} to an already-existing team (create it first with /team add). */
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
