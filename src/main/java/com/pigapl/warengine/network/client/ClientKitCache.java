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

    private static volatile boolean pickerReopen;

    static void requestPickerReopen() {
        pickerReopen = true;
    }

    /** One-shot, same as the confirm below: the tick loop opens the picker, then it's gone. */
    public static boolean consumePickerReopen() {
        boolean value = pickerReopen;
        pickerReopen = false;
        return value;
    }

    public record PendingConfirm(String kitId, List<String> lines) {}

    private static volatile PendingConfirm pendingConfirm;

    static void setPendingConfirm(PendingConfirm value) {
        pendingConfirm = value;
    }

    /** One-shot: the client tick loop opens the confirm screen, then it's gone. */
    public static PendingConfirm consumePendingConfirm() {
        PendingConfirm value = pendingConfirm;
        pendingConfirm = null;
        return value;
    }
}
