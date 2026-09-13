package com.pigapl.warengine.team;

import com.pigapl.warengine.state.WarState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Who is allowed to shape a team: commanders and squad leaders.
 *
 * <p>Event 1's complaint was "a random was taking most kits" - anyone could found a squad and reserve
 * the team's whole budget. The chain is now admin -> commander -> squad leader:</p>
 * <ul>
 *   <li><b>Admin</b> (op) does anything, and is the only one who appoints commanders.</li>
 *   <li><b>Commander</b> creates squads, appoints squad leaders, hands the role on, and edits ANY
 *       squad on their team.</li>
 *   <li><b>Squad leader</b> creates a squad and edits only the one they lead.</li>
 *   <li><b>Member</b> joins a squad and picks a kit. Nothing else.</li>
 * </ul>
 *
 * <p>Both roles are stored WITH the team they were given for, so switching team drops them rather than
 * silently making someone commander of their new side.</p>
 */
public final class RoleService {

    private RoleService() {}

    public static boolean isAdmin(ServerPlayer player) {
        return player.hasPermissions(2);
    }

    public static boolean isCommander(MinecraftServer server, ServerPlayer player) {
        String team = TeamService.getTeam(server, player);
        return team != null && team.equalsIgnoreCase(WarState.get(server).getCommanderOf(player.getUUID()));
    }

    public static boolean isAppointedLeader(MinecraftServer server, ServerPlayer player) {
        String team = TeamService.getTeam(server, player);
        return team != null && team.equalsIgnoreCase(WarState.get(server).getLeaderOf(player.getUUID()));
    }

    /** Founding a squad claims team budget, so it is the gate that fixes the event 1 complaint. */
    public static boolean canCreateSquad(MinecraftServer server, ServerPlayer player) {
        return isAdmin(player) || isCommander(server, player) || isAppointedLeader(server, player);
    }

    /**
     * Commanders edit any squad on their own team; a leader edits only the squad they actually lead,
     * so being made leader does not let you rewrite someone else's squad by joining it.
     */
    public static boolean canEditSquad(MinecraftServer server, ServerPlayer player, String squadId) {
        if (isAdmin(player)) {
            return true;
        }
        WarState.SquadRecord squad = WarState.get(server).getSquadRecord(squadId);
        if (squad == null) {
            return false;
        }
        if (isCommander(server, player) && squad.team.equalsIgnoreCase(TeamService.getTeam(server, player))) {
            return true;
        }
        return player.getUUID().equals(squad.leader);
    }

    /** Only a commander of that team may hand the role on, and only to someone on the same team. */
    public static boolean canHandOverTo(MinecraftServer server, ServerPlayer from, UUID to) {
        String team = TeamService.getTeam(server, from);
        if (team == null || !isCommander(server, from)) {
            return false;
        }
        ServerPlayer target = server.getPlayerList().getPlayer(to);
        return target != null && team.equalsIgnoreCase(TeamService.getTeam(server, target));
    }
}
