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
 * Weapons never handed out at kit-pick or on respawn, only at round start. One list across every kit
 * and team, stored by the same normalised identity reconcile uses. Marked by holding the weapon and
 * running {@code /kit scarce setscarce} - hand-editing JSON for a TACZ gun is miserable, since they
 * all share one item id. Persisted per server, not per world.
 */
public final class ScarceItems {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Codec<List<ItemStack>> LIST_CODEC = ItemStack.CODEC.listOf();

    private static final List<ItemStack> ITEMS = new ArrayList<>();

    private ScarceItems() {}

    public static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve(WarEngine.MODID).resolve("scarce.json");
    }

    public static List<ItemStack> all() {
        return Collections.unmodifiableList(ITEMS);
    }

    public static boolean isEmpty() {
        return ITEMS.isEmpty();
    }

    public static boolean isScarce(ItemStack stack) {
        return matchesAny(ITEMS, stack);
    }

    /**
     * The scarce-matching rule against an arbitrary list. Public so the admin client can pre-tick the
     * bulk screen from a snapshot - the rule must not be reimplemented there and drift.
     */
    public static boolean matchesAny(List<ItemStack> list, ItemStack stack) {
        if (stack.isEmpty() || list.isEmpty()) {
            return false;
        }
        for (ItemStack scarce : list) {
            if (KitService.sameForReconcile(scarce, stack)) {
                return true;
            }
        }
        return false;
    }

    /** Applies a whole bulk edit with ONE file write - add/remove each save, which is 30+ writes here. */
    public static int applyBulk(List<ItemStack> scarce, List<ItemStack> notScarce, MinecraftServer server)
            throws IOException {
        int changed = 0;
        for (ItemStack stack : notScarce) {
            if (stack.isEmpty()) {
                continue;
            }
            if (ITEMS.removeIf(item -> KitService.sameForReconcile(item, stack))) {
                changed++;
            }
        }
        for (ItemStack stack : scarce) {
            if (stack.isEmpty() || isScarce(stack)) {
                continue;
            }
            ITEMS.add(KitService.identity(stack));
            changed++;
        }
        if (changed > 0) {
            save(server);
        }
        return changed;
    }

    public static boolean add(ItemStack held, MinecraftServer server) throws IOException {
        if (held.isEmpty() || isScarce(held)) {
            return false;
        }
        ITEMS.add(KitService.identity(held));
        save(server);
        return true;
    }

    /** By list position, not identity - see {@code ServerboundAdminUnmarkScarceAtPayload} for why. */
    public static boolean removeAt(int index, MinecraftServer server) throws IOException {
        if (index < 0 || index >= ITEMS.size()) {
            return false;
        }
        ITEMS.remove(index);
        save(server);
        return true;
    }

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
