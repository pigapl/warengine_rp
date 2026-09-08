package com.pigapl.warengine.state;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Single source of truth for a running event, persisted with the overworld: each player's kit and
 * squad, the round state (running, end time, tickets, marker zone) and team bases.
 *
 * <p>Team membership deliberately does NOT live here - it uses vanilla scoreboard teams
 * ({@code TeamService}), which already persist and give color/friendly-fire/nametags. Squads have no
 * vanilla equivalent, so they live entirely here.</p>
 */
public final class WarState extends SavedData {
    private static final String NAME = "warengine_state";

    private static final Factory<WarState> FACTORY = new Factory<>(WarState::new, WarState::load);

    /** Per-player event data. Public mutable fields on purpose - this is a plain data holder. */
    public static final class PlayerRecord {
        /** Assigned kit/class id, or {@code null} if the player has not picked one. */
        public String kitId;
        /**
         * When {@link #kitId} last CHANGED (epoch millis), or {@code 0}. The scarce sweep breaks ties
         * for an over-limit kit by it - earliest assignment keeps the weapon. Re-picking the kit you
         * already hold does not bump it, so you keep your seniority.
         */
        public long kitAssignedAtMs;
        /** Assigned squad id, or {@code null} if the player has not picked/created one. */
        public String squadId;

        PlayerRecord() {}
    }

    /** One player-created squad. Membership is derived (see {@link #squadMembers}), not stored here. */
    public static final class SquadRecord {
        public final String id;
        public final String team;
        public String name;
        public int limit;
        /**
         * kit id -&gt; how much of the team's budget this squad claimed. Only meaningful for budgeted
         * kits ({@code TeamKits.budgetFor}), where this number IS the squad's cap on simultaneous
         * holders and 0 means the kit is not offered to it. Set at creation, then admin-only.
         */
        public final Map<String, Integer> kitReservations = new LinkedHashMap<>();

        SquadRecord(String id, String team, String name, int limit) {
            this.id = id;
            this.team = team;
            this.name = name;
            this.limit = limit;
        }
    }

    private final Map<UUID, PlayerRecord> players = new HashMap<>();
    private final Map<String, SquadRecord> squads = new LinkedHashMap<>();
    private int nextSquadSeq = 1;

    // ---- round state (one event-wide record, not per player) --------------------------------------
    private boolean roundActive;
    /** Wall-clock end of the round. Epoch millis, not a server tick - tick counts reset on restart. */
    private long roundEndEpochMillis;
    /** team id -> reinforcement tickets remaining. */
    private final Map<String, Integer> tickets = new LinkedHashMap<>();

    // Marker zone. All four null/absent = unset.
    private String zoneDim;
    private Integer zoneX;
    private Integer zoneY;
    private Integer zoneZ;
    private double zoneRadius = 5.0;

    /**
     * One entry in the capture-zone change log - the admin panel's "who has it / who had it" view.
     * Written only from {@code RoundService#tickCaptureZone}, which recomputes the holder every second
     * but appends here only on an actual transition.
     */
    public static final class CaptureLogEntry {
        public final long epochMillis;
        /** Team id, or {@code null} meaning the zone went empty or contested at this moment. */
        public final String team;
        /** Player names present at the moment of transition - the holder's, or (if contested) all of them. */
        public final List<String> players;

        CaptureLogEntry(long epochMillis, String team, List<String> players) {
            this.epochMillis = epochMillis;
            this.team = team;
            this.players = players;
        }
    }

    private String zoneHolderTeam;
    private final List<CaptureLogEntry> captureHistory = new ArrayList<>();
    private static final int MAX_CAPTURE_HISTORY = 50;

    /**
     * One team's base: respawn point, "TP all to bases" target, and the only place its players may
     * pick a kit (see {@code BaseService}). Here rather than {@code teamkits.json} because a position
     * is per-WORLD - on a fresh map those coordinates mean nothing, same as the capture zone.
     */
    public static final class TeamBase {
        public final String dim;
        public final double x;
        public final double y;
        public final double z;
        /** The setting admin's facing, so a mass teleport drops everyone looking the same way. */
        public final float yaw;

        TeamBase(String dim, double x, double y, double z, float yaw) {
            this.dim = dim;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
        }
    }

    /** Keyed by lowercased team id - scoreboard team names keep their case, this map does not. */
    private final Map<String, TeamBase> bases = new LinkedHashMap<>();

    public WarState() {}

    /** Gets (or creates) the state attached to the overworld. */
    public static WarState get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    public PlayerRecord record(UUID id) {
        return players.computeIfAbsent(id, k -> new PlayerRecord());
    }

    /** Assigned kit id for a player, or {@code null}. */
    public String getKit(UUID id) {
        PlayerRecord r = players.get(id);
        return r == null ? null : r.kitId;
    }

    public void setKit(UUID id, String kitId) {
        PlayerRecord r = record(id);
        if (!java.util.Objects.equals(r.kitId, kitId)) {
            r.kitAssignedAtMs = kitId == null ? 0L : System.currentTimeMillis();
        }
        r.kitId = kitId;
        setDirty();
    }

    public void clearKit(UUID id) {
        PlayerRecord r = players.get(id);
        if (r != null && r.kitId != null) {
            r.kitId = null;
            r.kitAssignedAtMs = 0L;
            setDirty();
        }
    }

    /** When the current kit was assigned (epoch millis), or {@code 0}. See {@link PlayerRecord#kitAssignedAtMs}. */
    public long getKitAssignedAtMs(UUID id) {
        PlayerRecord r = players.get(id);
        return r == null ? 0L : r.kitAssignedAtMs;
    }

    // ---- squads -----------------------------------------------------------------------------------

    /** Assigned squad id for a player, or {@code null}. */
    public String getSquad(UUID id) {
        PlayerRecord r = players.get(id);
        return r == null ? null : r.squadId;
    }

    public void setSquad(UUID id, String squadId) {
        record(id).squadId = squadId;
        setDirty();
    }

    public void clearSquad(UUID id) {
        PlayerRecord r = players.get(id);
        if (r != null && r.squadId != null) {
            r.squadId = null;
            setDirty();
        }
    }

    /** Every squad, keyed by id, in creation order. Iterate for display; use the mutators to change. */
    public Map<String, SquadRecord> squads() {
        return Collections.unmodifiableMap(squads);
    }

    public SquadRecord getSquadRecord(String squadId) {
        return squadId == null ? null : squads.get(squadId);
    }

    /**
     * Creates and registers a squad, returning its generated id (never reused within this world).
     * Positive {@code reservations} are copied on as-is - {@code SquadService} must have validated
     * them against the team budget first.
     */
    public SquadRecord createSquad(String team, String name, int limit, Map<String, Integer> reservations) {
        String id = "sq" + (nextSquadSeq++);
        SquadRecord record = new SquadRecord(id, team, name, limit);
        if (reservations != null) {
            reservations.forEach((kit, count) -> {
                if (count != null && count > 0) {
                    record.kitReservations.put(kit, count);
                }
            });
        }
        squads.put(id, record);
        setDirty();
        return record;
    }

    /** Sets ({@code count > 0}) or clears ({@code count <= 0}) one squad's reservation for a kit. */
    public void setSquadReservation(String squadId, String kitId, int count) {
        SquadRecord r = squads.get(squadId);
        if (r == null) {
            return;
        }
        if (count > 0) {
            r.kitReservations.put(kitId, count);
        } else {
            r.kitReservations.remove(kitId);
        }
        setDirty();
    }

    /** Removes a squad's registration (its members are NOT auto-cleared - callers do that first). */
    public void deleteSquad(String squadId) {
        if (squads.remove(squadId) != null) {
            setDirty();
        }
    }

    /** Every player id currently assigned to {@code squadId} - online or not. */
    public List<UUID> squadMembers(String squadId) {
        List<UUID> out = new ArrayList<>();
        players.forEach((id, r) -> {
            if (squadId.equals(r.squadId)) {
                out.add(id);
            }
        });
        return out;
    }

    /** No-op if the squad no longer exists (e.g. a stale admin-panel click after it emptied out). */
    public void renameSquad(String squadId, String name) {
        SquadRecord r = squads.get(squadId);
        if (r != null) {
            r.name = name;
            setDirty();
        }
    }

    /** No-op if the squad no longer exists - see {@link #renameSquad}. */
    public void setSquadLimit(String squadId, int limit) {
        SquadRecord r = squads.get(squadId);
        if (r != null) {
            r.limit = limit;
            setDirty();
        }
    }

    // ---- round ----------------------------------------------------------------------------------

    public boolean roundActive() {
        return roundActive;
    }

    public long roundEndEpochMillis() {
        return roundEndEpochMillis;
    }

    /** Live ticket map, keyed by team id. Iterate for display; use {@link #setTickets} to mutate. */
    public Map<String, Integer> tickets() {
        return tickets;
    }

    public int getTickets(String team) {
        return tickets.getOrDefault(team, 0);
    }

    public void setTickets(String team, int count) {
        tickets.put(team, Math.max(0, count));
        setDirty();
    }

    public void startRound(long endEpochMillis, Map<String, Integer> initialTickets) {
        roundActive = true;
        roundEndEpochMillis = endEpochMillis;
        tickets.clear();
        tickets.putAll(initialTickets);
        setDirty();
    }

    public void endRound() {
        roundActive = false;
        setDirty();
    }

    /** Extends (positive) or shortens (negative) a running war's clock. No-op if none is running. */
    public void adjustRoundEnd(long deltaMillis) {
        if (roundActive) {
            roundEndEpochMillis += deltaMillis;
            setDirty();
        }
    }

    // ---- zone ----------------------------------------------------------------------------------

    public boolean hasZone() {
        return zoneDim != null && zoneX != null && zoneY != null && zoneZ != null;
    }

    public void setZone(String dim, int x, int y, int z, double radius) {
        zoneDim = dim;
        zoneX = x;
        zoneY = y;
        zoneZ = z;
        zoneRadius = radius;
        setDirty();
    }

    public void clearZone() {
        zoneDim = null;
        zoneX = null;
        zoneY = null;
        zoneZ = null;
        setDirty();
    }

    public String zoneDim() {
        return zoneDim;
    }

    public int zoneX() {
        return zoneX;
    }

    public int zoneY() {
        return zoneY;
    }

    public int zoneZ() {
        return zoneZ;
    }

    public double zoneRadius() {
        return zoneRadius;
    }

    // ---- capture history --------------------------------------------------------------------------

    /** The team currently holding the zone uncontested, or {@code null} (empty or contested). */
    public String zoneHolderTeam() {
        return zoneHolderTeam;
    }

    /**
     * Records a holder transition - call only when the holder actually changed, not every tick, or
     * the log fills with duplicate entries a second apart. Trims to {@link #MAX_CAPTURE_HISTORY}.
     */
    public void setZoneHolder(String team, List<String> players) {
        zoneHolderTeam = team;
        captureHistory.add(new CaptureLogEntry(System.currentTimeMillis(), team, List.copyOf(players)));
        while (captureHistory.size() > MAX_CAPTURE_HISTORY) {
            captureHistory.remove(0);
        }
        setDirty();
    }

    /** Oldest first. */
    public List<CaptureLogEntry> captureHistory() {
        return Collections.unmodifiableList(captureHistory);
    }

    // ---- team bases -------------------------------------------------------------------------------

    /** {@code null} if that team has no base set (which means no base restrictions apply to it). */
    public TeamBase getBase(String team) {
        return team == null ? null : bases.get(team.toLowerCase(Locale.ROOT));
    }

    public void setBase(String team, String dim, double x, double y, double z, float yaw) {
        bases.put(team.toLowerCase(Locale.ROOT), new TeamBase(dim, x, y, z, yaw));
        setDirty();
    }

    /** @return whether there was one to remove. */
    public boolean clearBase(String team) {
        if (team != null && bases.remove(team.toLowerCase(Locale.ROOT)) != null) {
            setDirty();
            return true;
        }
        return false;
    }

    /** Every set base, keyed by lowercased team id. */
    public Map<String, TeamBase> bases() {
        return Collections.unmodifiableMap(bases);
    }

    // ---- persistence -------------------------------------------------------------------------------

    public static WarState load(CompoundTag tag, HolderLookup.Provider registries) {
        WarState state = new WarState();
        ListTag list = tag.getList("players", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            if (!e.hasUUID("uuid")) continue;
            PlayerRecord r = new PlayerRecord();
            if (e.contains("kit", Tag.TAG_STRING)) {
                r.kitId = e.getString("kit");
            }
            if (e.contains("kitAt", Tag.TAG_LONG)) {
                r.kitAssignedAtMs = e.getLong("kitAt");
            }
            if (e.contains("squad", Tag.TAG_STRING)) {
                r.squadId = e.getString("squad");
            }
            state.players.put(e.getUUID("uuid"), r);
        }

        if (tag.contains("squads", Tag.TAG_LIST)) {
            ListTag squadList = tag.getList("squads", Tag.TAG_COMPOUND);
            int maxSeq = 0;
            for (int i = 0; i < squadList.size(); i++) {
                CompoundTag s = squadList.getCompound(i);
                String id = s.getString("id");
                SquadRecord record = new SquadRecord(id, s.getString("team"), s.getString("name"), s.getInt("limit"));
                if (s.contains("reservations", Tag.TAG_COMPOUND)) {
                    CompoundTag res = s.getCompound("reservations");
                    for (String kit : res.getAllKeys()) {
                        int n = res.getInt(kit);
                        if (n > 0) {
                            record.kitReservations.put(kit, n);
                        }
                    }
                }
                state.squads.put(id, record);
                // Recover the counter from the highest "sqN" seen, so a squad created after a reload
                // never collides with one loaded from disk.
                if (id.startsWith("sq")) {
                    try {
                        maxSeq = Math.max(maxSeq, Integer.parseInt(id.substring(2)));
                    } catch (NumberFormatException ignored) {
                        // hand-edited id - not part of the sequence
                    }
                }
            }
            state.nextSquadSeq = maxSeq + 1;
        }

        state.roundActive = tag.getBoolean("roundActive");
        state.roundEndEpochMillis = tag.getLong("roundEndEpochMillis");
        if (tag.contains("tickets", Tag.TAG_COMPOUND)) {
            CompoundTag tk = tag.getCompound("tickets");
            for (String team : tk.getAllKeys()) {
                state.tickets.put(team, tk.getInt(team));
            }
        }
        if (tag.contains("zone", Tag.TAG_COMPOUND)) {
            CompoundTag z = tag.getCompound("zone");
            state.zoneDim = z.getString("dim");
            state.zoneX = z.getInt("x");
            state.zoneY = z.getInt("y");
            state.zoneZ = z.getInt("z");
            state.zoneRadius = z.contains("radius") ? z.getDouble("radius") : 5.0;
        }

        if (tag.contains("zoneHolderTeam", Tag.TAG_STRING)) {
            state.zoneHolderTeam = tag.getString("zoneHolderTeam");
        }
        if (tag.contains("captureHistory", Tag.TAG_LIST)) {
            ListTag hist = tag.getList("captureHistory", Tag.TAG_COMPOUND);
            for (int i = 0; i < hist.size(); i++) {
                CompoundTag h = hist.getCompound(i);
                String team = h.contains("team", Tag.TAG_STRING) ? h.getString("team") : null;
                List<String> players = new ArrayList<>();
                ListTag pl = h.getList("players", Tag.TAG_STRING);
                for (int j = 0; j < pl.size(); j++) {
                    players.add(pl.getString(j));
                }
                state.captureHistory.add(new CaptureLogEntry(h.getLong("t"), team, players));
            }
        }

        if (tag.contains("bases", Tag.TAG_LIST)) {
            ListTag baseList = tag.getList("bases", Tag.TAG_COMPOUND);
            for (int i = 0; i < baseList.size(); i++) {
                CompoundTag b = baseList.getCompound(i);
                state.bases.put(b.getString("team").toLowerCase(Locale.ROOT),
                        new TeamBase(b.getString("dim"), b.getDouble("x"), b.getDouble("y"),
                                b.getDouble("z"), b.getFloat("yaw")));
            }
        }
        return state;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        players.forEach((id, r) -> {
            CompoundTag e = new CompoundTag();
            e.putUUID("uuid", id);
            if (r.kitId != null) {
                e.putString("kit", r.kitId);
                if (r.kitAssignedAtMs != 0L) {
                    e.putLong("kitAt", r.kitAssignedAtMs);
                }
            }
            if (r.squadId != null) {
                e.putString("squad", r.squadId);
            }
            list.add(e);
        });
        tag.put("players", list);

        ListTag squadList = new ListTag();
        squads.forEach((id, record) -> {
            CompoundTag s = new CompoundTag();
            s.putString("id", record.id);
            s.putString("team", record.team);
            s.putString("name", record.name);
            s.putInt("limit", record.limit);
            if (!record.kitReservations.isEmpty()) {
                CompoundTag res = new CompoundTag();
                record.kitReservations.forEach(res::putInt);
                s.put("reservations", res);
            }
            squadList.add(s);
        });
        tag.put("squads", squadList);

        tag.putBoolean("roundActive", roundActive);
        tag.putLong("roundEndEpochMillis", roundEndEpochMillis);
        CompoundTag tk = new CompoundTag();
        tickets.forEach(tk::putInt);
        tag.put("tickets", tk);
        if (hasZone()) {
            CompoundTag z = new CompoundTag();
            z.putString("dim", zoneDim);
            z.putInt("x", zoneX);
            z.putInt("y", zoneY);
            z.putInt("z", zoneZ);
            z.putDouble("radius", zoneRadius);
            tag.put("zone", z);
        }

        if (zoneHolderTeam != null) {
            tag.putString("zoneHolderTeam", zoneHolderTeam);
        }
        ListTag hist = new ListTag();
        captureHistory.forEach(e -> {
            CompoundTag h = new CompoundTag();
            h.putLong("t", e.epochMillis);
            if (e.team != null) {
                h.putString("team", e.team);
            }
            ListTag pl = new ListTag();
            e.players.forEach(name -> pl.add(net.minecraft.nbt.StringTag.valueOf(name)));
            h.put("players", pl);
            hist.add(h);
        });
        tag.put("captureHistory", hist);

        ListTag baseList = new ListTag();
        bases.forEach((team, base) -> {
            CompoundTag b = new CompoundTag();
            b.putString("team", team);
            b.putString("dim", base.dim);
            b.putDouble("x", base.x);
            b.putDouble("y", base.y);
            b.putDouble("z", base.z);
            b.putFloat("yaw", base.yaw);
            baseList.add(b);
        });
        tag.put("bases", baseList);
        return tag;
    }
}
