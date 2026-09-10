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
 * A player-formed subgroup within a team, between team and kit in the pick flow. Squads have no life
 * outside one event, so they live entirely in {@link WarState}.
 *
 * <p><b>Two limits, do not confuse them.</b> The squad ROSTER limit (this class) counts EVERY member,
 * online or not. The kit SLOT limit ({@code KitService#countUsing}) counts ONLINE members only.</p>
 */
public final class SquadService {

    public static final int NAME_MAX_LENGTH = 24;
    public static final int MIN_LIMIT = 1;
    public static final int MAX_LIMIT = 100;

    private SquadService() {}

    public enum CreateResult { OK, NO_TEAM, BLANK_NAME, NAME_TOO_LONG, BAD_LIMIT, BUDGET_EXCEEDED }
    public enum JoinResult { OK, UNKNOWN_SQUAD, WRONG_TEAM, FULL }
    public enum UpdateResult { OK, UNKNOWN_SQUAD, BLANK_NAME, NAME_TOO_LONG, BAD_LIMIT, NO_BUDGET, BUDGET_EXCEEDED }

    public static String getSquadId(MinecraftServer server, ServerPlayer player) {
        return getSquadId(server, player.getUUID());
    }

    public static String getSquadId(MinecraftServer server, java.util.UUID playerId) {
        return WarState.get(server).getSquad(playerId);
    }

    public static WarState.SquadRecord getSquad(MinecraftServer server, String squadId) {
        return WarState.get(server).getSquadRecord(squadId);
    }

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

    public static int memberCount(MinecraftServer server, String squadId) {
        return WarState.get(server).squadMembers(squadId).size();
    }

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

    /** Does NOT evict members when lowered below the current roster - future joins only. */
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
     * Creating IS picking - the caller joins the squad and vacates their previous one.
     * {@code reservations} is checked against what is still unreserved team-wide; a squad may take
     * the whole remainder. Kits the team has no budget for are ignored.
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

        leave(player);
        WarState st = WarState.get(server);
        WarState.SquadRecord record = st.createSquad(team, name, limit, clean);
        st.setSquad(player.getUUID(), record.id);
        return CreateResult.OK;
    }

    /** {@code BUDGET_EXCEEDED} counts the new value plus what the team's OTHER squads already hold. */
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

    public static String leave(ServerPlayer player) {
        return kick(player.server, player.getUUID());
    }

    /** Deletes the squad if that was its last member - an empty one is just clutter in the picker. */
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
