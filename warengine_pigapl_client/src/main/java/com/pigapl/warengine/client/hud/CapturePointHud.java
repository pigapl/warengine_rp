package com.pigapl.warengine.client.hud;

import com.pigapl.warengine.client.WarClientConfig;
import com.pigapl.warengine.client.WarEngineClient;
import com.pigapl.warengine.network.CapturePointStatus;
import com.pigapl.warengine.network.TeamTicketEntry;
import com.pigapl.warengine.network.client.ClientCapturePointCache;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.scores.PlayerTeam;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The always-on capture readout: your ticket bar left, point pills centre, other teams right.
 *
 * <p>The deliberate counterpart to the in-world markers: the world shows a point only where you could
 * physically see it, so knowing WHO OWNS WHAT without walking there is this HUD's job. Team colours
 * resolve locally off the already-synced scoreboard - no colour data on the wire.</p>
 */
public final class CapturePointHud implements LayeredDraw.Layer {

    private static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(WarEngineClient.MODID, "capture_points");

    private static final int NEUTRAL = 0xFF6E6E6E;
    private static final int TRACK = 0x80000000;
    private static final int OUTLINE = 0xFF000000;
    private static final int OWN_OUTLINE = 0xFFFFFFFF;
    private static final int OWN_LABEL_MAX = 10;
    private static final int CONTESTED = 0xFFFFD24A;
    private static final int TEXT = 0xFFFFFFFF;

    private static final int PILL_W = 28;
    private static final int PILL_H = 14;
    private static final int CAPTURE_BAR_H = 3;
    private static final int PILL_GAP = 3;

    private static final int TICKET_BAR_W = 80;
    private static final int TICKET_BAR_H = 6;
    private static final int TICKET_BAR_GAP = 2;
    private static final int SIDE_GAP = 8;

    private static final CapturePointHud INSTANCE = new CapturePointHud();

    private CapturePointHud() {}

    public static void register(RegisterGuiLayersEvent event) {
        // Above the boss bar so a capture readout is never hidden behind one, and clear of the action
        // bar and hotbar at the bottom of the screen.
        event.registerAbove(VanillaGuiLayers.BOSS_OVERLAY, ID, INSTANCE);
    }

    /**
     * Redraws over an open screen from the SAME code and data, so {@code HudOptionsScreen} previews
     * the real HUD. Deliberately not a mock-up - a second implementation would drift.
     */
    public static void renderOverScreen(GuiGraphics graphics) {
        INSTANCE.draw(graphics);
    }

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        draw(graphics);
    }

    private void draw(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.level == null || mc.player == null) {
            return;
        }
        List<CapturePointStatus> points = ClientCapturePointCache.points();
        if (points.isEmpty()) {
            return;
        }

        float scale = WarClientConfig.scale();
        // Everything below is laid out in unscaled units against this virtual width, so a scale change
        // moves the whole block as one piece and never breaks the centering.
        int screenW = (int) (graphics.guiWidth() / scale);
        int centreX = screenW / 2;
        int top = WarClientConfig.HUD_OFFSET_Y.get();

        graphics.pose().pushPose();
        graphics.pose().scale(scale, scale, 1.0f);
        try {
            int pillRowW = 0;
            if (WarClientConfig.SHOW_POINTS.get()) {
                pillRowW = points.size() * PILL_W + (points.size() - 1) * PILL_GAP;
                int x = centreX - pillRowW / 2;
                for (CapturePointStatus point : points) {
                    drawPill(graphics, mc, point, x, top);
                    x += PILL_W + PILL_GAP;
                }
            }
            drawTicketSides(graphics, mc, centreX, pillRowW, top);
        } finally {
            graphics.pose().popPose();
        }
    }

    private void drawPill(GuiGraphics graphics, Minecraft mc, CapturePointStatus point, int x, int y) {
        int owner = teamColor(mc, point.owner(), NEUTRAL);

        graphics.fill(x - 1, y - 1, x + PILL_W + 1, y + PILL_H + 1,
                point.contested() ? CONTESTED : OUTLINE);
        graphics.fill(x, y, x + PILL_W, y + PILL_H, owner);

        String label = point.id().toUpperCase(Locale.ROOT);
        graphics.drawString(mc.font, label, x + (PILL_W - mc.font.width(label)) / 2, y + 3, TEXT, true);

        if (!WarClientConfig.SHOW_CAPTURE_BARS.get()) {
            return;
        }
        int barTop = y + PILL_H + 1;
        graphics.fill(x, barTop, x + PILL_W, barTop + CAPTURE_BAR_H, TRACK);
        if (point.capturing().isEmpty() || point.captureTotal() <= 0) {
            return;
        }
        // Interpolated across the gap between the roughly-1/s server pushes so it creeps rather than
        // stepping; clamped so a late packet cannot overshoot the bar.
        double elapsed = (System.currentTimeMillis() - ClientCapturePointCache.lastChangeMillis()) / 1000.0;
        double units = point.contested() ? point.progress() : point.progress() + Math.min(1.0, elapsed);
        double frac = Math.max(0.0, Math.min(1.0, units / point.captureTotal()));
        int fill = (int) Math.round(PILL_W * frac);
        if (fill > 0) {
            graphics.fill(x, barTop, x + fill, barTop + CAPTURE_BAR_H,
                    teamColor(mc, point.capturing(), NEUTRAL));
        }
    }

    private void drawTicketSides(GuiGraphics graphics, Minecraft mc, int centreX, int pillRowW, int top) {
        if (!ClientCapturePointCache.warActive()) {
            return;
        }
        boolean bars = WarClientConfig.SHOW_TICKET_BARS.get();
        boolean counts = WarClientConfig.SHOW_TICKET_COUNTS.get();
        boolean rate = WarClientConfig.SHOW_TICKET_RATE.get();
        boolean eta = WarClientConfig.SHOW_TIME_TO_WIN.get();
        if (!bars && !counts && !rate && !eta) {
            return;
        }
        List<TeamTicketEntry> tickets = ClientCapturePointCache.tickets();
        if (tickets.isEmpty()) {
            return;
        }
        String ownTeam = mc.player != null && mc.player.getTeam() != null
                ? mc.player.getTeam().getName() : null;

        List<TeamTicketEntry> mine = new ArrayList<>();
        List<TeamTicketEntry> theirs = new ArrayList<>();
        for (TeamTicketEntry entry : tickets) {
            (entry.team().equals(ownTeam) ? mine : theirs).add(entry);
        }
        // No team yet (spectating, or still at the team picker): show everyone on the right rather
        // than silently dropping half the readout.
        int inner = pillRowW / 2 + SIDE_GAP;

        int y = top;
        for (TeamTicketEntry entry : mine) {
            drawTicketBar(graphics, mc, entry, centreX - inner, y, true, true, bars, counts, rate, eta);
            y += TICKET_BAR_H + TICKET_BAR_GAP + (rate || eta ? 10 : 0);
        }
        y = top;
        for (TeamTicketEntry entry : theirs) {
            drawTicketBar(graphics, mc, entry, centreX + inner, y, false, false, bars, counts, rate, eta);
            y += TICKET_BAR_H + TICKET_BAR_GAP + (rate || eta ? 10 : 0);
        }
    }

    private void drawTicketBar(GuiGraphics graphics, Minecraft mc, TeamTicketEntry entry, int innerX,
                               int y, boolean leftward, boolean own, boolean bars, boolean counts,
                               boolean showRate, boolean showEta) {
        int cap = Math.max(1, ClientCapturePointCache.ticketCap());
        int color = teamColor(mc, entry.team(), NEUTRAL);
        double frac = Math.max(0.0, Math.min(1.0, entry.tickets() / (double) cap));

        int outerX = leftward ? innerX - TICKET_BAR_W : innerX + TICKET_BAR_W;
        if (bars) {
            int trackLeft = Math.min(innerX, outerX);
            int trackRight = Math.max(innerX, outerX);
            graphics.fill(trackLeft, y, trackRight, y + TICKET_BAR_H, TRACK);
            int fill = (int) Math.round(TICKET_BAR_W * frac);
            if (fill > 0) {
                int fillLeft = leftward ? innerX - fill : innerX;
                graphics.fill(fillLeft, y, fillLeft + fill, y + TICKET_BAR_H, color);
            }
            graphics.renderOutline(trackLeft, y, TICKET_BAR_W, TICKET_BAR_H,
                    own ? OWN_OUTLINE : OUTLINE);
        }

        int textX = bars ? outerX : innerX;
        if (counts) {
            String n = own ? teamLabel(mc, entry.team()) + " " + entry.tickets()
                    : Integer.toString(entry.tickets());
            int w = mc.font.width(n);
            graphics.drawString(mc.font, n, leftward ? textX - w - 3 : textX + 3,
                    y - 1, color, true);
        }

        if (!showRate && !showEta) {
            return;
        }
        int income = 0;
        for (CapturePointStatus p : ClientCapturePointCache.points()) {
            if (!p.contested() && p.owner().equals(entry.team())) {
                income++;
            }
        }
        StringBuilder sb = new StringBuilder();
        if (showRate) {
            sb.append('+').append(income).append("/s");
        }
        if (showEta) {
            if (sb.length() > 0) {
                sb.append("  ");
            }
            sb.append(income == 0 ? "--" : mmss((cap - entry.tickets()) / income));
        }
        String line = sb.toString();
        int lineW = mc.font.width(line);
        int lineX = leftward ? Math.min(innerX, outerX) : Math.max(innerX, outerX) - lineW;
        graphics.drawString(mc.font, line, lineX, y + TICKET_BAR_H + 2, color, true);
    }

    /** Short display name for the player's own team, so the HUD says WHICH team is theirs. */
    private static String teamLabel(Minecraft mc, String teamName) {
        String label = teamName;
        if (mc.level != null) {
            PlayerTeam team = mc.level.getScoreboard().getPlayerTeam(teamName);
            if (team != null) {
                label = team.getDisplayName().getString();
            }
        }
        label = label.toUpperCase(Locale.ROOT);
        return label.length() > OWN_LABEL_MAX ? label.substring(0, OWN_LABEL_MAX) : label;
    }

    private static String mmss(long seconds) {
        long s = Math.max(0L, seconds);
        return (s / 60L) + ":" + String.format(Locale.ROOT, "%02d", s % 60L);
    }

    private static int teamColor(Minecraft mc, String teamName, int fallback) {
        if (teamName == null || teamName.isEmpty() || mc.level == null) {
            return fallback;
        }
        PlayerTeam team = mc.level.getScoreboard().getPlayerTeam(teamName);
        Integer rgb = team == null || team.getColor() == null ? null : team.getColor().getColor();
        return rgb == null ? fallback : 0xFF000000 | rgb;
    }
}
