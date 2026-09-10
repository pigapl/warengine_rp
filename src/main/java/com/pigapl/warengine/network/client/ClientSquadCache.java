package com.pigapl.warengine.network.client;

import com.pigapl.warengine.network.SquadEntry;

import java.util.List;

public final class ClientSquadCache {

    private static volatile List<SquadEntry> squads = List.of();
    private static volatile String squadId = "";
    private static volatile int revision = 0;

    private ClientSquadCache() {}

    public static List<SquadEntry> squads() {
        return squads;
    }

    public static String squadId() {
        return squadId;
    }

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
