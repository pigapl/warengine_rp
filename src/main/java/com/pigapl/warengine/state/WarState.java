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
 * Single source of truth for a running event, persisted with the overworld.
 *
 * <p>Team membership deliberately does NOT live here - it uses vanilla scoreboard teams, which
 * already give persistence, colour, friendly-fire and nametags. Squads have no vanilla equivalent.</p>
 */
public final class WarState extends SavedData {
    private static final String NAME = "warengine_state";

    private static final Factory<WarState> FACTORY = new Factory<>(WarState::new, WarState::load);

    public static final class PlayerRecord {
        public String kitId;
        /** When {@link #kitId} last CHANGED. Re-picking the same kit does not bump it - seniority is kept. */
        public long kitAssignedAtMs;
        public String squadId;

        PlayerRecord() {}
    }

    public static final class SquadRecord {
        public final String id;
        public final String team;
        public String name;
        public int limit;
        /** For budgeted kits this number IS the squad's cap; 0 means the kit is not offered to it. */
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

    private boolean roundActive;
    /** Wall-clock end of the round. Epoch millis, not a server tick - tick counts reset on restart. */
    private long roundEndEpochMillis;
    private final Map<String, Integer> tickets = new LinkedHashMap<>();

    /**
     * Ownership is STICKY - {@link #owner} earns whether or not anyone stands here. {@link #progress}
     * is the capture bar and only ever belongs to {@link #capturingTeam}.
     */
    public static final class CapturePoint {
        public final String id;
        public final String dim;
        public final int x;
        public final int y;
        public final int z;
        public double radius;
        public String owner;
        public String capturingTeam;
        public int progress;

        CapturePoint(String id, String dim, int x, int y, int z, double radius) {
            this.id = id;
            this.dim = dim;
            this.x = x;
            this.y = y;
            this.z = z;
            this.radius = radius;
        }
    }

    private final Map<String, CapturePoint> points = new LinkedHashMap<>();

    /** Capture change log. Appended only on an actual flip, not every tick. */
    public static final class CaptureLogEntry {
        public final long epochMillis;
        public final String point;
        public final String team;
        public final List<String> players;

        CaptureLogEntry(long epochMillis, String point, String team, List<String> players) {
            this.epochMillis = epochMillis;
            this.point = point;
            this.team = team;
            this.players = players;
        }
    }

    private final List<CaptureLogEntry> captureHistory = new ArrayList<>();
    private static final int MAX_CAPTURE_HISTORY = 50;

    /**
     * Respawn point, TP-all target, and the only place its players may pick a kit. Here rather than
     * {@code teamkits.json} because a position is per-WORLD - on a fresh map it means nothing.
     */
    public static final class TeamBase {
        public final String dim;
        public final double x;
        public final double y;
        public final double z;
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

    public static WarState get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    public PlayerRecord record(UUID id) {
        return players.computeIfAbsent(id, k -> new PlayerRecord());
    }

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

    public long getKitAssignedAtMs(UUID id) {
        PlayerRecord r = players.get(id);
        return r == null ? 0L : r.kitAssignedAtMs;
    }


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

    public Map<String, SquadRecord> squads() {
        return Collections.unmodifiableMap(squads);
    }

    public SquadRecord getSquadRecord(String squadId) {
        return squadId == null ? null : squads.get(squadId);
    }

    /** {@code reservations} are copied as-is - {@code SquadService} must validate them against the budget first. */
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

    public List<UUID> squadMembers(String squadId) {
        List<UUID> out = new ArrayList<>();
        players.forEach((id, r) -> {
            if (squadId.equals(r.squadId)) {
                out.add(id);
            }
        });
        return out;
    }

    public void renameSquad(String squadId, String name) {
        SquadRecord r = squads.get(squadId);
        if (r != null) {
            r.name = name;
            setDirty();
        }
    }

    public void setSquadLimit(String squadId, int limit) {
        SquadRecord r = squads.get(squadId);
        if (r != null) {
            r.limit = limit;
            setDirty();
        }
    }


    public boolean roundActive() {
        return roundActive;
    }

    public long roundEndEpochMillis() {
        return roundEndEpochMillis;
    }

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

    public void adjustRoundEnd(long deltaMillis) {
        if (roundActive) {
            roundEndEpochMillis += deltaMillis;
            setDirty();
        }
    }


    /** Ids are stored uppercased, so /war point tp a finds point A. */
    private static String pointKey(String id) {
        return id == null ? null : id.toUpperCase(Locale.ROOT);
    }

    public List<CapturePoint> points() {
        return List.copyOf(points.values());
    }

    public CapturePoint getPoint(String id) {
        return id == null ? null : points.get(pointKey(id));
    }

    public boolean hasPoints() {
        return !points.isEmpty();
    }

    public CapturePoint addPoint(String id, String dim, int x, int y, int z, double radius) {
        CapturePoint p = new CapturePoint(pointKey(id), dim, x, y, z, radius);
        points.put(p.id, p);
        setDirty();
        return p;
    }

    public boolean removePoint(String id) {
        if (points.remove(pointKey(id)) == null) {
            return false;
        }
        setDirty();
        return true;
    }

    public int clearPoints() {
        int n = points.size();
        points.clear();
        setDirty();
        return n;
    }

    public void setPointProgress(String id, String team, int progress) {
        CapturePoint p = getPoint(id);
        if (p == null) {
            return;
        }
        p.capturingTeam = team;
        p.progress = team == null ? 0 : Math.max(0, progress);
        setDirty();
    }

    /** Call ONLY on an actual change of owner - every tick fills the history with duplicates. */
    public void setPointOwner(String id, String team, List<String> players) {
        CapturePoint p = getPoint(id);
        if (p == null) {
            return;
        }
        p.owner = team;
        p.capturingTeam = null;
        p.progress = 0;
        captureHistory.add(new CaptureLogEntry(System.currentTimeMillis(), p.id, team, List.copyOf(players)));
        while (captureHistory.size() > MAX_CAPTURE_HISTORY) {
            captureHistory.remove(0);
        }
        setDirty();
    }

    public void resetPointsToNeutral() {
        for (CapturePoint p : points.values()) {
            p.owner = null;
            p.capturingTeam = null;
            p.progress = 0;
        }
        setDirty();
    }

    public List<CaptureLogEntry> captureHistory() {
        return Collections.unmodifiableList(captureHistory);
    }


    /** {@code null} if that team has no base set (which means no base restrictions apply to it). */
    public TeamBase getBase(String team) {
        return team == null ? null : bases.get(team.toLowerCase(Locale.ROOT));
    }

    public void setBase(String team, String dim, double x, double y, double z, float yaw) {
        bases.put(team.toLowerCase(Locale.ROOT), new TeamBase(dim, x, y, z, yaw));
        setDirty();
    }

    public boolean clearBase(String team) {
        if (team != null && bases.remove(team.toLowerCase(Locale.ROOT)) != null) {
            setDirty();
            return true;
        }
        return false;
    }

    public Map<String, TeamBase> bases() {
        return Collections.unmodifiableMap(bases);
    }


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
        if (tag.contains("points", Tag.TAG_LIST)) {
            ListTag pointList = tag.getList("points", Tag.TAG_COMPOUND);
            for (int i = 0; i < pointList.size(); i++) {
                CompoundTag p = pointList.getCompound(i);
                CapturePoint point = state.addPoint(p.getString("id"), p.getString("dim"),
                        p.getInt("x"), p.getInt("y"), p.getInt("z"), p.getDouble("radius"));
                if (p.contains("owner", Tag.TAG_STRING)) {
                    point.owner = p.getString("owner");
                }
                if (p.contains("capturing", Tag.TAG_STRING)) {
                    point.capturingTeam = p.getString("capturing");
                    point.progress = p.getInt("progress");
                }
            }
        } else if (tag.contains("zone", Tag.TAG_COMPOUND)) {
            // Migration off the single pre-2026-09-09 marker zone: it becomes point A rather than
            // silently vanishing from worlds set up before capture points existed.
            CompoundTag z = tag.getCompound("zone");
            state.addPoint("A", z.getString("dim"), z.getInt("x"), z.getInt("y"), z.getInt("z"),
                    z.contains("radius") ? z.getDouble("radius") : 5.0);
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
                // "point" is absent on entries written before capture points were named.
                state.captureHistory.add(new CaptureLogEntry(h.getLong("t"), h.getString("point"),
                        team, players));
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
        ListTag pointList = new ListTag();
        points.forEach((id, point) -> {
            CompoundTag p = new CompoundTag();
            p.putString("id", point.id);
            p.putString("dim", point.dim);
            p.putInt("x", point.x);
            p.putInt("y", point.y);
            p.putInt("z", point.z);
            p.putDouble("radius", point.radius);
            // Ownership and a half-filled bar both survive a mid-war restart, same as the round clock.
            if (point.owner != null) {
                p.putString("owner", point.owner);
            }
            if (point.capturingTeam != null) {
                p.putString("capturing", point.capturingTeam);
                p.putInt("progress", point.progress);
            }
            pointList.add(p);
        });
        tag.put("points", pointList);

        ListTag hist = new ListTag();
        captureHistory.forEach(e -> {
            CompoundTag h = new CompoundTag();
            h.putLong("t", e.epochMillis);
            if (e.point != null) {
                h.putString("point", e.point);
            }
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
