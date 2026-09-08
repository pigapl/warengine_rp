package com.pigapl.warengine.squad;

import com.pigapl.warengine.kit.KitService;
import com.pigapl.warengine.kit.KitStorage;
import com.pigapl.warengine.kit.TeamKits;
import com.pigapl.warengine.state.WarState;
import com.pigapl.warengine.team.TeamService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Squads: a player-formed subgroup within a team, sitting between team and kit in the pick flow.
 * Unlike teams (vanilla scoreboard) and kits (config JSON), squads have no life outside one event -
 * players form them live during staging, so they live entirely in {@link WarState}.
 *
 * <p>Two limits here must not be confused. The <b>squad roster limit</b> (this class) caps how many
 * players may be assigned, counting EVERY member online or not - a roster is a membership, not a live
 * resource. The <b>kit slot limit</b> ({@code KitService#countUsing}) caps how many squad-mates hold
 * one kit at once, counting ONLINE members only.</p>
 *
 * <p>Validate-and-mutate lives here rather than in the command/payload layer, same reason as
 * {@code KitService#assign}: the two callers must never drift on what is a legal join/create.</p>
 */
public final class SquadService {

    /** Free-text squad name cap - it renders on a card in everyone's picker, keep it short. */
    public static final int NAME_MAX_LENGTH = 24;
    public static final int MIN_LIMIT = 1;
    public static final int MAX_LIMIT = 100;

    private SquadService() {}

    public enum CreateResult { OK, NO_TEAM, BLANK_NAME, NAME_TOO_LONG, BAD_LIMIT, BUDGET_EXCEEDED }
    public enum JoinResult { OK, UNKNOWN_SQUAD, WRONG_TEAM, FULL }
    public enum UpdateResult { OK, UNKNOWN_SQUAD, BLANK_NAME, NAME_TOO_LONG, BAD_LIMIT, NO_BUDGET, BUDGET_EXCEEDED }

    /** This player's squad id, or {@code null}. */
    public static String getSquadId(MinecraftServer server, ServerPlayer player) {
        return getSquadId(server, player.getUUID());
    }

    /** Same as above, but works for an offline player too - a squad roster tracks by id, not presence. */
    public static String getSquadId(MinecraftServer server, java.util.UUID playerId) {
        return WarState.get(server).getSquad(playerId);
    }

    public static WarState.SquadRecord getSquad(MinecraftServer server, String squadId) {
        return WarState.get(server).getSquadRecord(squadId);
    }

    /** Every squad belonging to {@code team}, in creation order. */
    public static List<WarState.SquadRecord> squadsFor(MinecraftServer server, String team) {
        List<WarState.SquadRecord> out = new ArrayList<>();
        if (team == null) {
            return out;
        }
        for (WarState.SquadRecord record : WarState.get(server).squads().values()) {
            if (team.equalsIgnoreCase(record.team)) {
                out.add(record);
            }
        }
        return out;
    }

    /** How many players are currently assigned to a squad - online or not, see the class javadoc. */
    public static int memberCount(MinecraftServer server, String squadId) {
        return WarState.get(server).squadMembers(squadId).size();
    }

    /** Admin-panel rename - same name rules as {@link #create}. */
    public static UpdateResult rename(MinecraftServer server, String squadId, String rawName) {
        WarState st = WarState.get(server);
        if (st.getSquadRecord(squadId) == null) {
            return UpdateResult.UNKNOWN_SQUAD;
        }
        String name = rawName == null ? "" : rawName.trim();
        if (name.isEmpty()) {
            return UpdateResult.BLANK_NAME;
        }
        if (name.length() > NAME_MAX_LENGTH) {
            return UpdateResult.NAME_TOO_LONG;
        }
        st.renameSquad(squadId, name);
        return UpdateResult.OK;
    }

    /**
     * Admin-panel roster-limit change. Does NOT evict members if lowered below the current roster -
     * future joins only, same as a kit limit never un-equipping anyone when lowered.
     */
    public static UpdateResult setLimit(MinecraftServer server, String squadId, int limit) {
        WarState st = WarState.get(server);
        if (st.getSquadRecord(squadId) == null) {
            return UpdateResult.UNKNOWN_SQUAD;
        }
        if (limit < MIN_LIMIT || limit > MAX_LIMIT) {
            return UpdateResult.BAD_LIMIT;
        }
        st.setSquadLimit(squadId, limit);
        return UpdateResult.OK;
    }

    /**
     * Creates a squad on the caller's team and joins them to it - creating IS picking. Vacates their
     * previous squad first, like {@link #join} does when switching.
     *
     * <p>{@code reservations} (kit id -&gt; count) is how many of each budgeted kit the squad claims
     * from its team's budget. Kits the team has no budget for are ignored; each count is checked
     * against what is still unreserved team-wide, and a squad may take the whole remainder. Empty or
     * {@code null} means a squad that only sees non-budgeted kits.</p>
     */
    public static CreateResult create(ServerPlayer player, String rawName, int limit,
                                      Map<String, Integer> reservations) {
        MinecraftServer server = player.server;
        String team = TeamService.getTeam(server, player);
        if (team == null) {
            return CreateResult.NO_TEAM;
        }
        String name = rawName == null ? "" : rawName.trim();
        if (name.isEmpty()) {
            return CreateResult.BLANK_NAME;
        }
        if (name.length() > NAME_MAX_LENGTH) {
            return CreateResult.NAME_TOO_LONG;
        }
        if (limit < MIN_LIMIT || limit > MAX_LIMIT) {
            return CreateResult.BAD_LIMIT;
        }

        // The old squad's reservations still count against the budget UNLESS founding this one will
        // dissolve it (the caller is its last member) - then those slots are about to free up.
        String oldSquad = getSquadId(server, player);
        String freeingSquad = oldSquad != null
                && WarState.get(server).squadMembers(oldSquad).size() == 1 ? oldSquad : null;

        Map<String, Integer> clean = new LinkedHashMap<>();
        if (reservations != null) {
            for (Map.Entry<String, Integer> e : reservations.entrySet()) {
                String kit = KitStorage.normalizeId(e.getKey());
                int want = e.getValue() == null ? 0 : e.getValue();
                if (want <= 0 || !TeamKits.hasBudget(team, kit)) {
                    continue;
                }
                if (want > KitService.unreservedBudget(server, team, kit, freeingSquad)) {
                    return CreateResult.BUDGET_EXCEEDED;
                }
                clean.put(kit, want);
            }
        }

        leave(player); // vacate any existing squad before founding a new one
        WarState st = WarState.get(server);
        WarState.SquadRecord record = st.createSquad(team, name, limit, clean);
        st.setSquad(player.getUUID(), record.id);
        return CreateResult.OK;
    }

    /**
     * Sets ({@code count > 0}) or clears one squad's kit reservation - the admin-side edit.
     * {@code NO_BUDGET} if the team has no budget for that kit; {@code BUDGET_EXCEEDED} if the new
     * count plus what the team's OTHER squads hold would exceed it.
     */
    public static UpdateResult setReservation(MinecraftServer server, String squadId, String kitId, int count) {
        WarState st = WarState.get(server);
        WarState.SquadRecord squad = st.getSquadRecord(squadId);
        if (squad == null) {
            return UpdateResult.UNKNOWN_SQUAD;
        }
        String kit = KitStorage.normalizeId(kitId);
        if (count < 0) {
            return UpdateResult.BAD_LIMIT;
        }
        if (count > 0) {
            if (!TeamKits.hasBudget(squad.team, kit)) {
                return UpdateResult.NO_BUDGET;
            }
            if (count > KitService.unreservedBudget(server, squad.team, kit, squadId)) {
                return UpdateResult.BUDGET_EXCEEDED;
            }
        }
        st.setSquadReservation(squadId, kit, count);
        return UpdateResult.OK;
    }

    /** Joins an existing squad on the caller's own team, leaving whatever squad they were in first. */
    public static JoinResult join(ServerPlayer player, String squadId) {
        MinecraftServer server = player.server;
        WarState st = WarState.get(server);
        WarState.SquadRecord record = st.getSquadRecord(squadId);
        if (record == null) {
            return JoinResult.UNKNOWN_SQUAD;
        }
        String team = TeamService.getTeam(server, player);
        if (team == null || !team.equalsIgnoreCase(record.team)) {
            return JoinResult.WRONG_TEAM;
        }
        boolean alreadyIn = record.id.equals(st.getSquad(player.getUUID()));
        if (!alreadyIn && record.limit > 0 && memberCount(server, record.id) >= record.limit) {
            return JoinResult.FULL;
        }
        leave(player);
        st.setSquad(player.getUUID(), record.id);
        return JoinResult.OK;
    }

    /** Vacates the caller's current squad, if any - see {@link #kick} for the shared logic. */
    public static String leave(ServerPlayer player) {
        return kick(player.server, player.getUUID());
    }

    /**
     * Vacates {@code targetId}'s squad - by self-request ({@link #leave}) or an admin kick (works on
     * offline members too, a roster tracks by id not presence). Deletes the squad if that was its last
     * member: squads are ephemeral, so an empty one is just clutter in the picker.
     *
     * @return the squad id vacated, or {@code null} if they were in none
     */
    public static String kick(MinecraftServer server, java.util.UUID targetId) {
        WarState st = WarState.get(server);
        String squadId = st.getSquad(targetId);
        if (squadId == null) {
            return null;
        }
        st.clearSquad(targetId);
        if (st.squadMembers(squadId).isEmpty()) {
            st.deleteSquad(squadId);
        }
        return squadId;
    }
}
