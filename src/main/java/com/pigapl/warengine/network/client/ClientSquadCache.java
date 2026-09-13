package com.pigapl.warengine.network.client;

import com.pigapl.warengine.network.SquadEntry;

import java.util.List;

public final class ClientSquadCache {

    private static volatile List<SquadEntry> squads = List.of();
    private static volatile String squadId = "";
    private static volatile boolean canCreate = false;
    private static volatile boolean commander = false;
    private static volatile int revision = 0;

    private ClientSquadCache() {}

    public static List<SquadEntry> squads() {
        return squads;
    }

    public static String squadId() {
        return squadId;
    }

    /** Server's answer, not a guess - the client never decides who may found a squad. */
    public static boolean canCreate() {
        return canCreate;
    }

    public static boolean commander() {
        return commander;
    }

    public static int revision() {
        return revision;
    }

    static void setSquads(List<SquadEntry> value) {
        squads = value;
        revision++;
    }

    static void setSquadId(String value, boolean mayCreate, boolean isCommander) {
        squadId = value;
        canCreate = mayCreate;
        commander = isCommander;
        revision++;
    }
}
