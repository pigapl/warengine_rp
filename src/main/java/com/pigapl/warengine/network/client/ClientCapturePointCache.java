package com.pigapl.warengine.network.client;

import com.pigapl.warengine.network.CapturePointStatus;
import com.pigapl.warengine.network.ClientboundCapturePointsPayload;
import com.pigapl.warengine.network.TeamTicketEntry;

import java.util.List;

/**
 * Last capture-point state pushed by the server. {@link #lastChangeMillis} is here so the HUD can
 * interpolate a bar between the roughly one-per-second pushes instead of stepping.
 */
public final class ClientCapturePointCache {

    private static volatile List<CapturePointStatus> points = List.of();
    private static volatile List<TeamTicketEntry> tickets = List.of();
    private static volatile int ticketCap = 0;
    private static volatile boolean warActive = false;
    private static volatile long timeLeftMillis = 0L;
    private static volatile long lastChangeMillis = 0L;

    private ClientCapturePointCache() {}

    public static List<CapturePointStatus> points() {
        return points;
    }

    public static List<TeamTicketEntry> tickets() {
        return tickets;
    }

    public static int ticketCap() {
        return ticketCap;
    }

    public static boolean warActive() {
        return warActive;
    }

    public static long timeLeftMillis() {
        return timeLeftMillis;
    }

    public static long lastChangeMillis() {
        return lastChangeMillis;
    }

    static void set(ClientboundCapturePointsPayload payload) {
        points = payload.points();
        tickets = payload.tickets();
        ticketCap = payload.ticketCap();
        warActive = payload.warActive();
        timeLeftMillis = payload.timeLeftMillis();
        lastChangeMillis = System.currentTimeMillis();
    }
}
