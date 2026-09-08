package com.pigapl.warengine.kit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.pigapl.warengine.WarEngine;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The global list of "scarce" weapons (RPG, sniper, LMG, ...): never handed out at kit-pick time or
 * refilled on respawn, only issued once at round start by {@link KitService#issueScarceWeapons}.
 * One list across every kit and team.
 *
 * <p>Marked by holding the weapon and running {@code /kit scarce setscarce} - hand-editing JSON to
 * identify a TACZ gun is miserable, since every one of them is {@code tacz:modern_kinetic_gun} and
 * only a {@code GunId} tag in component data tells them apart.</p>
 *
 * <p>Matched (and stored) by the same normalised identity reconcile uses -
 * {@code KitService#identity}/{@code #sameForReconcile}, package-visible for this class.
 * Persisted at {@code config/warengine_pigapl/scarce.json}: per server, not per world.</p>
 */
public final class ScarceItems {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Codec<List<ItemStack>> LIST_CODEC = ItemStack.CODEC.listOf();

    /** Normalised identities only - see the class javadoc. */
    private static final List<ItemStack> ITEMS = new ArrayList<>();

    private ScarceItems() {}

    public static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve(WarEngine.MODID).resolve("scarce.json");
    }

    /** Every scarce identity, in marking order - for {@code /kit scarce list}. */
    public static List<ItemStack> all() {
        return Collections.unmodifiableList(ITEMS);
    }

    public static boolean isEmpty() {
        return ITEMS.isEmpty();
    }

    /** Whether {@code stack} (from a kit definition or a live inventory slot) is a scarce weapon. */
    public static boolean isScarce(ItemStack stack) {
        if (stack.isEmpty() || ITEMS.isEmpty()) {
            return false;
        }
        for (ItemStack scarce : ITEMS) {
            if (KitService.sameForReconcile(scarce, stack)) {
                return true;
            }
        }
        return false;
    }

    /** @return false if {@code held}'s identity was already marked scarce. */
    public static boolean add(ItemStack held, MinecraftServer server) throws IOException {
        if (held.isEmpty() || isScarce(held)) {
            return false;
        }
        ITEMS.add(KitService.identity(held));
        save(server);
        return true;
    }

    /**
     * Admin-panel row removal, by position in the list the caller was last shown rather than by
     * identity - see {@code ServerboundAdminUnmarkScarceAtPayload} for the race-tolerance reasoning.
     */
    public static boolean removeAt(int index, MinecraftServer server) throws IOException {
        if (index < 0 || index >= ITEMS.size()) {
            return false;
        }
        ITEMS.remove(index);
        save(server);
        return true;
    }

    /** @return false if {@code held}'s identity was not marked scarce. */
    public static boolean remove(ItemStack held, MinecraftServer server) throws IOException {
        if (held.isEmpty()) {
            return false;
        }
        boolean removed = ITEMS.removeIf(existing -> KitService.sameForReconcile(existing, held));
        if (removed) {
            save(server);
        }
        return removed;
    }

    /** Wipes the in-memory list and reloads it from disk. @return number of scarce identities loaded. */
    public static int loadAll(MinecraftServer server) {
        ITEMS.clear();
        Path path = file();
        if (!Files.exists(path)) {
            return 0;
        }
        try {
            RegistryOps<JsonElement> ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
            JsonElement json = JsonParser.parseString(Files.readString(path));
            List<ItemStack> loaded = LIST_CODEC.parse(ops, json)
                    .getOrThrow(msg -> new IllegalStateException(msg));
            ITEMS.addAll(loaded);
        } catch (Exception e) {
            WarEngine.LOGGER.error("Failed to load {}", path, e);
        }
        return ITEMS.size();
    }

    private static void save(MinecraftServer server) throws IOException {
        RegistryOps<JsonElement> ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        JsonElement json = LIST_CODEC.encodeStart(ops, ITEMS)
                .getOrThrow(msg -> new IOException("could not encode scarce list: " + msg));
        Files.createDirectories(file().getParent());
        Files.writeString(file(), GSON.toJson(json));
    }
}
