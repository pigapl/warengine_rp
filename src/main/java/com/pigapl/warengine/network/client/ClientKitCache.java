package com.pigapl.warengine.network.client;

import com.pigapl.warengine.network.KitCatalogEntry;

import java.util.List;

public final class ClientKitCache {

    private static volatile List<KitCatalogEntry> catalog = List.of();
    private static volatile String kitId = "";
    private static volatile int revision = 0;

    private ClientKitCache() {}

    public static List<KitCatalogEntry> catalog() {
        return catalog;
    }

    public static String kitId() {
        return kitId;
    }

    /**
     * Bumped on every server push so an open screen knows its cards are stale. Client thread only and
     * readers only test for inequality, so the non-atomic increment is fine.
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
