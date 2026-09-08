package com.pigapl.warengine.network.client;

import com.pigapl.warengine.network.SquadEntry;

import java.util.List;

/**
 * The latest server-pushed squad state, mirroring {@link ClientKitCache} exactly - same reasoning
 * for living in the base mod (payload handlers live here) and same revision-counter trick so an
 * open screen can tell its cards are stale without polling every field by hand.
 */
public final class ClientSquadCache {

    private static volatile List<SquadEntry> squads = List.of();
    private static volatile String squadId = "";
    private static volatile int revision = 0;

    private ClientSquadCache() {}

    public static List<SquadEntry> squads() {
        return squads;
    }

    /** The player's own assigned squad id, or {@code ""} if none. */
    public static String squadId() {
        return squadId;
    }

    /** Bumped on every server push - see {@link ClientKitCache#revision()} for why. */
    public static int revision() {
        return revision;
    }

    static void setSquads(List<SquadEntry> value) {
        squads = value;
        revision++;
    }

    static void setSquadId(String value) {
        squadId = value;
        revision++;
    }
}
