package com.pigapl.warengine.kit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.pigapl.warengine.WarEngine;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Loads and saves kit JSON files under {@code config/warengine_pigapl/kits/<id>.json}
 * and keeps them in memory for the running server. Reloadable at runtime via {@code /kit reload}.
 */
public final class KitStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Map<String, KitDefinition> KITS = new HashMap<>();

    private KitStorage() {}

    public static Path dir() {
        return FMLPaths.CONFIGDIR.get().resolve(WarEngine.MODID).resolve("kits");
    }

    public static String normalizeId(String id) {
        return id.toLowerCase(Locale.ROOT);
    }

    public static Set<String> ids() {
        return Collections.unmodifiableSet(KITS.keySet());
    }

    public static Optional<KitDefinition> get(String id) {
        return Optional.ofNullable(KITS.get(normalizeId(id)));
    }

    /** Wipes the in-memory cache and reloads every {@code *.json} in the kit directory. */
    public static int loadAll(MinecraftServer server) {
        KITS.clear();
        Path dir = dir();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            WarEngine.LOGGER.error("Could not create kit directory {}", dir, e);
            return 0;
        }

        RegistryOps<JsonElement> ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.getFileName().toString().endsWith(".json")).forEach(p -> {
                String fileName = p.getFileName().toString();
                String id = normalizeId(fileName.substring(0, fileName.length() - ".json".length()));
                try {
                    JsonElement json = JsonParser.parseString(Files.readString(p));
                    KitDefinition def = KitDefinition.CODEC.parse(ops, json)
                            .getOrThrow(msg -> new IllegalStateException("kit '" + id + "': " + msg));
                    KITS.put(id, def);
                } catch (Exception e) {
                    WarEngine.LOGGER.error("Failed to load kit file {}", p, e);
                }
            });
        } catch (IOException e) {
            WarEngine.LOGGER.error("Could not list kit directory {}", dir, e);
        }
        return KITS.size();
    }

    /** Serializes a kit to disk and updates the in-memory cache. */
    public static void save(String id, KitDefinition def, MinecraftServer server) throws IOException {
        String norm = normalizeId(id);
        RegistryOps<JsonElement> ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        JsonElement json = KitDefinition.CODEC.encodeStart(ops, def)
                .getOrThrow(msg -> new IOException("could not encode kit '" + norm + "': " + msg));
        Files.createDirectories(dir());
        Files.writeString(dir().resolve(norm + ".json"), GSON.toJson(json));
        KITS.put(norm, def);
    }

    /** @return true if a file was actually removed. */
    public static boolean delete(String id) throws IOException {
        String norm = normalizeId(id);
        KITS.remove(norm);
        return Files.deleteIfExists(dir().resolve(norm + ".json"));
    }
}
