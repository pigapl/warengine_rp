package com.pigapl.warengine.network.client;

import com.pigapl.warengine.network.KitBudgetEntry;

import java.util.List;

/**
 * The player's team's kit budgets, as last pushed by the server for the squad-picker Create screen's
 * reservation steppers. Same base-mod-lives-here / revision-counter design as {@link ClientKitCache}
 * and {@link ClientSquadCache}.
 */
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
