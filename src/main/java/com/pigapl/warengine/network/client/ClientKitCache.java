package com.pigapl.warengine.network.client;

import com.pigapl.warengine.network.KitCatalogEntry;

import java.util.List;

/**
 * The latest server-pushed kit state, read by the client addon's pickers. Lives in the base mod
 * because the payload handlers that write it do ({@link ClientPayloadHandlers}). Client-only:
 * nothing server-side calls into it.
 */
public final class ClientKitCache {

    private static volatile List<KitCatalogEntry> catalog = List.of();
    private static volatile String kitId = "";
    private static volatile int revision = 0;

    private ClientKitCache() {}

    public static List<KitCatalogEntry> catalog() {
        return catalog;
    }

    /** The player's own assigned kit id, or {@code ""} if none. */
    public static String kitId() {
        return kitId;
    }

    /**
     * Bumped on every server push, so an open screen knows its cards are stale - without it a kit
     * whose last slot was just taken keeps rendering as available until the screen is reopened.
     * Client thread only (handlers go through {@code enqueueWork}) and readers only test for
     * inequality, so the non-atomic increment is fine.
     */
    public static int revision() {
        return revision;
    }

    static void setCatalog(List<KitCatalogEntry> value) {
        catalog = value;
        revision++;
    }

    static void setKitId(String value) {
        kitId = value;
        revision++;
    }
}
