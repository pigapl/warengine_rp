package com.pigapl.warengine.kit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pigapl.warengine.WarEngine;
import com.pigapl.warengine.team.TeamService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * The per-event setup at {@code config/warengine_pigapl/teamkits.json}: which teams exist, what they
 * look like, and which kits each may use.
 *
 * <p><b>Why this holds teams and not just kits:</b> {@code config/} is per server and survives world
 * swaps; scoreboard teams live in {@code <world>/data/scoreboard.dat} and do not. A fresh map would
 * otherwise mean rebuilding teams by hand - {@code /kit teams restore} recreates them from here.</p>
 */
public final class TeamKits {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public static final String ALL_TEAMS = "*";

    /**
     * {@code budgets} is how many this team may field IN TOTAL across its squads, claimed as
     * reservations at squad creation. No entry means the kit follows its plain per-squad limit.
     * Budgets on the {@code "*"} pseudo-team are ignored.
     */
    public record TeamDef(String id, String color, String displayName, List<String> kits,
                          Map<String, Integer> budgets) {
        public TeamDef withKits(List<String> newKits) {
            return new TeamDef(id, color, displayName, newKits, budgets);
        }

        public TeamDef withBudgets(Map<String, Integer> newBudgets) {
            return new TeamDef(id, color, displayName, kits, newBudgets);
        }
    }

    private static final Map<String, TeamDef> TEAMS = new LinkedHashMap<>();

    private TeamKits() {}

    public static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve(WarEngine.MODID).resolve("teamkits.json");
    }


    public static List<String> kitsFor(String teamId) {
        LinkedHashSet<String> out = new LinkedHashSet<>(kitsOf(ALL_TEAMS));
        if (teamId != null) {
            out.addAll(kitsOf(KitStorage.normalizeId(teamId)));
        }
        return List.copyOf(out);
    }

    private static List<String> kitsOf(String normalizedTeam) {
        TeamDef def = TEAMS.get(normalizedTeam);
        return def == null ? List.of() : def.kits();
    }

    public static boolean isAllowed(String teamId, String kitId) {
        return kitsFor(teamId).contains(KitStorage.normalizeId(kitId));
    }


    public static int budgetFor(String teamId, String kitId) {
        if (teamId == null) {
            return 0;
        }
        TeamDef def = TEAMS.get(KitStorage.normalizeId(teamId));
        return def == null ? 0 : def.budgets().getOrDefault(KitStorage.normalizeId(kitId), 0);
    }

    public static boolean hasBudget(String teamId, String kitId) {
        return budgetFor(teamId, kitId) > 0;
    }

    public static Map<String, Integer> budgetsOf(String teamId) {
        if (teamId == null) {
            return Map.of();
        }
        TeamDef def = TEAMS.get(KitStorage.normalizeId(teamId));
        return def == null ? Map.of() : Map.copyOf(def.budgets());
    }

    public static void setBudget(String teamId, String kitId, int count) throws IOException {
        String team = KitStorage.normalizeId(teamId);
        String kit = KitStorage.normalizeId(kitId);
        TeamDef def = TEAMS.computeIfAbsent(team,
                t -> new TeamDef(t, null, null, new ArrayList<>(), new LinkedHashMap<>()));
        Map<String, Integer> budgets = new LinkedHashMap<>(def.budgets());
        if (count > 0) {
            budgets.put(kit, count);
        } else {
            budgets.remove(kit);
        }
        TEAMS.put(team, def.withBudgets(budgets));
        save();
    }

    public static Map<String, TeamDef> all() {
        return Collections.unmodifiableMap(TEAMS);
    }

    public static boolean assign(String kitId, String teamId) throws IOException {
        String kit = KitStorage.normalizeId(kitId);
        String team = KitStorage.normalizeId(teamId);
        TeamDef def = TEAMS.computeIfAbsent(team,
                t -> new TeamDef(t, null, null, new ArrayList<>(), new LinkedHashMap<>()));
        if (def.kits().contains(kit)) {
            return false;
        }
        List<String> kits = new ArrayList<>(def.kits());
        kits.add(kit);
        TEAMS.put(team, def.withKits(kits));
        save();
        return true;
    }

    public static boolean unassign(String kitId, String teamId) throws IOException {
        String kit = KitStorage.normalizeId(kitId);
        String team = KitStorage.normalizeId(teamId);
        TeamDef def = TEAMS.get(team);
        if (def == null || !def.kits().contains(kit)) {
            return false;
        }
        List<String> kits = new ArrayList<>(def.kits());
        kits.remove(kit);
        // Keep an empty team entry - it still carries color/displayName for restore. Only "*" is
        // worth dropping, since it has nothing else.
        if (kits.isEmpty() && team.equals(ALL_TEAMS)) {
            TEAMS.remove(team);
        } else {
            TEAMS.put(team, def.withKits(kits));
        }
        save();
        return true;
    }

    public static int removeKitEverywhere(String kitId) throws IOException {
        String kit = KitStorage.normalizeId(kitId);
        int removed = 0;
        for (Map.Entry<String, TeamDef> entry : new ArrayList<>(TEAMS.entrySet())) {
            TeamDef def = entry.getValue();
            boolean inKits = def.kits().contains(kit);
            boolean inBudgets = def.budgets().containsKey(kit);
            if (!inKits && !inBudgets) {
                continue;
            }
            List<String> kits = new ArrayList<>(def.kits());
            kits.remove(kit);
            Map<String, Integer> budgets = new LinkedHashMap<>(def.budgets());
            budgets.remove(kit);
            if (kits.isEmpty() && budgets.isEmpty() && entry.getKey().equals(ALL_TEAMS)) {
                TEAMS.remove(entry.getKey());
            } else {
                TEAMS.put(entry.getKey(), new TeamDef(def.id(), def.color(), def.displayName(), kits, budgets));
            }
            removed++;
        }
        if (removed > 0) {
            save();
        }
        return removed;
    }


    public enum TeamResult { OK, NOT_FOUND, BAD_COLOR, NOT_A_TEAM }

    private static boolean isColorName(String name) {
        ChatFormatting formatting = ChatFormatting.getByName(name);
        return formatting != null && formatting.isColor();
    }

    public static TeamResult addTeam(MinecraftServer server, String teamId, String color) throws IOException {
        String team = KitStorage.normalizeId(teamId);
        if (team.equals(ALL_TEAMS)) {
            return TeamResult.NOT_A_TEAM;
        }
        if (color != null && !isColorName(color)) {
            return TeamResult.BAD_COLOR;
        }
        Scoreboard scoreboard = server.getScoreboard();
        PlayerTeam live = scoreboard.getPlayerTeam(team);
        if (live == null) {
            live = scoreboard.addPlayerTeam(team);
        }
        if (color != null) {
            live.setColor(ChatFormatting.getByName(color));
        }
        TeamDef existing = TEAMS.get(team);
        List<String> kits = existing == null ? new ArrayList<>() : existing.kits();
        String keptDisplayName = existing == null ? null : existing.displayName();
        String keptColor = color != null ? color : (existing == null ? null : existing.color());
        Map<String, Integer> keptBudgets = existing == null ? new LinkedHashMap<>() : existing.budgets();
        TEAMS.put(team, new TeamDef(team, keptColor, keptDisplayName, kits, keptBudgets));
        save();
        return TeamResult.OK;
    }

    public static TeamResult removeTeam(MinecraftServer server, String teamId) throws IOException {
        String team = KitStorage.normalizeId(teamId);
        Scoreboard scoreboard = server.getScoreboard();
        PlayerTeam live = scoreboard.getPlayerTeam(team);
        boolean saved = TEAMS.remove(team) != null;
        if (live == null && !saved) {
            return TeamResult.NOT_FOUND;
        }
        if (live != null) {
            scoreboard.removePlayerTeam(live);
        }
        save();
        return TeamResult.OK;
    }

    public static int restore(MinecraftServer server) {
        Scoreboard scoreboard = server.getScoreboard();
        int created = 0;
        for (TeamDef def : TEAMS.values()) {
            if (def.id().equals(ALL_TEAMS) || scoreboard.getPlayerTeam(def.id()) != null) {
                continue;
            }
            PlayerTeam team = scoreboard.addPlayerTeam(def.id());
            if (def.color() != null && isColorName(def.color())) {
                team.setColor(ChatFormatting.getByName(def.color()));
            }
            if (def.displayName() != null) {
                team.setDisplayName(Component.literal(def.displayName()));
            }
            created++;
        }
        return created;
    }


    public static int loadAll() {
        TEAMS.clear();
        Path path = file();
        if (!Files.exists(path)) {
            return 0;
        }
        try {
            JsonElement root = JsonParser.parseString(Files.readString(path));
            if (!root.isJsonObject()) {
                WarEngine.LOGGER.error("{} is not a JSON object - ignoring it", path);
                return 0;
            }
            for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject().entrySet()) {
                String team = KitStorage.normalizeId(entry.getKey());
                TeamKits.TeamDef def = parseTeam(team, entry.getValue());
                if (def != null) {
                    TEAMS.put(team, def);
                }
            }
        } catch (Exception e) {
            WarEngine.LOGGER.error("Failed to load {}", path, e);
        }
        return TEAMS.size();
    }

    private static TeamDef parseTeam(String team, JsonElement value) {
        List<String> kits = new ArrayList<>();
        if (value.isJsonArray()) {
            for (JsonElement kit : value.getAsJsonArray()) {
                kits.add(KitStorage.normalizeId(kit.getAsString()));
            }
            return new TeamDef(team, null, null, kits, new LinkedHashMap<>());
        }
        if (!value.isJsonObject()) {
            WarEngine.LOGGER.warn("teamkits.json entry '{}' is neither an object nor a list - skipped", team);
            return null;
        }
        JsonObject obj = value.getAsJsonObject();
        if (obj.has("kits") && obj.get("kits").isJsonArray()) {
            for (JsonElement kit : obj.getAsJsonArray("kits")) {
                kits.add(KitStorage.normalizeId(kit.getAsString()));
            }
        }
        Map<String, Integer> budgets = new LinkedHashMap<>();
        if (obj.has("budgets") && obj.get("budgets").isJsonObject()) {
            for (Map.Entry<String, JsonElement> b : obj.getAsJsonObject("budgets").entrySet()) {
                try {
                    int n = b.getValue().getAsInt();
                    if (n > 0) {
                        budgets.put(KitStorage.normalizeId(b.getKey()), n);
                    }
                } catch (RuntimeException ex) {
                    WarEngine.LOGGER.warn("teamkits.json team '{}' budget '{}' is not a number - skipped",
                            team, b.getKey());
                }
            }
        }
        String color = obj.has("color") ? obj.get("color").getAsString() : null;
        String displayName = obj.has("displayName") ? obj.get("displayName").getAsString() : null;
        if (color != null && !isColorName(color)) {
            WarEngine.LOGGER.warn("teamkits.json team '{}' has unknown color '{}' - ignoring the color", team, color);
            color = null;
        }
        return new TeamDef(team, color, displayName, kits, budgets);
    }

    private static void save() throws IOException {
        JsonObject root = new JsonObject();
        TEAMS.forEach((team, def) -> {
            JsonObject obj = new JsonObject();
            if (def.color() != null) {
                obj.addProperty("color", def.color());
            }
            if (def.displayName() != null) {
                obj.addProperty("displayName", def.displayName());
            }
            JsonArray kits = new JsonArray();
            def.kits().forEach(kits::add);
            obj.add("kits", kits);
            if (!def.budgets().isEmpty()) {
                JsonObject budgets = new JsonObject();
                def.budgets().forEach(budgets::addProperty);
                obj.add("budgets", budgets);
            }
            root.add(team, obj);
        });
        Files.createDirectories(file().getParent());
        Files.writeString(file(), GSON.toJson(root));
    }


    public static void warnAboutMissingKits() {
        TEAMS.forEach((team, def) -> {
            for (String kit : def.kits()) {
                if (KitStorage.get(kit).isEmpty()) {
                    WarEngine.LOGGER.warn("teamkits.json maps team '{}' to kit '{}', which does not exist",
                            team, kit);
                }
            }
        });
    }

    public static void warnAboutMissingTeams(MinecraftServer server) {
        List<String> existing = TeamService.ids(server);
        for (String team : TEAMS.keySet()) {
            if (team.equals(ALL_TEAMS)) {
                continue;
            }
            if (existing.stream().noneMatch(t -> t.equalsIgnoreCase(team))) {
                WarEngine.LOGGER.warn("teamkits.json has team '{}', which does not exist in this world - "
                        + "run /kit teams restore (scoreboard teams are per-world)", team);
            }
        }
    }
}
