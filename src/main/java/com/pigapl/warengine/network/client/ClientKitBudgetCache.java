package com.pigapl.warengine.network.client;

import com.pigapl.warengine.network.KitBudgetEntry;

import java.util.List;

public final class ClientKitBudgetCache {

    private static volatile List<KitBudgetEntry> entries = List.of();
    private static volatile int revision = 0;

    private ClientKitBudgetCache() {}

    public static List<KitBudgetEntry> entries() {
        return entries;
    }

    public static int revision() {
        return revision;
    }

    static void set(List<KitBudgetEntry> value) {
        entries = value;
        revision++;
    }
}
