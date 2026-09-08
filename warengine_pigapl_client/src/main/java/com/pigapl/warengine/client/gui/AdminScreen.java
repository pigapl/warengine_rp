package com.pigapl.warengine.client.gui;

import com.pigapl.warengine.network.AdminHistoryEntry;
import com.pigapl.warengine.network.AdminTeamInfo;
import com.pigapl.warengine.network.AdminZone;
import com.pigapl.warengine.network.AdminZoneStatus;
import com.pigapl.warengine.network.ClientboundAdminSnapshotPayload;
import com.pigapl.warengine.network.ServerboundAdminAdjustTimePayload;
import com.pigapl.warengine.network.ServerboundAdminEndWarPayload;
import com.pigapl.warengine.network.ServerboundAdminResetTicketsPayload;
import com.pigapl.warengine.network.ServerboundAdminSetTicketCapPayload;
import com.pigapl.warengine.network.ServerboundAdminStartWarPayload;
import com.pigapl.warengine.network.ServerboundAdminTeleportAllToBasesPayload;
import com.pigapl.warengine.network.ServerboundAdminTeleportToZonePayload;
import com.pigapl.warengine.network.ServerboundRequestAdminSnapshotPayload;
import com.pigapl.warengine.network.client.ClientAdminCache;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * The admin panel: start/stop the war, plus a live read-only view of tickets, rosters, the capture
 * zone and its history.
 *
 * <p>Opened only by the server's {@link com.pigapl.warengine.network.ClientboundOpenAdminScreenPayload}
 * ({@code /warstate admin}) - unlike the pickers, there is no state to auto-derive "you owe this
 * screen" from. Polls {@link ServerboundRequestAdminSnapshotPayload} once a second while open;
 * {@code render} reads {@link ClientAdminCache} live rather than caching a copy.</p>
 */
public final class AdminScreen extends Screen {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT);
    private static final int[] START_PRESETS_MIN = {5, 10, 20};
    private static final int POLL_INTERVAL_TICKS = 20;
    private static final int LINE_HEIGHT = 10;

    private static final int CONTROL_ROW_HEIGHT = 20;
    private static final int CONTROL_GAP = 4;

    private int pollTicks = 0;
    private int historyScroll = 0; // lines scrolled down from the newest entry

    private EditBox ticketCapBox;

    /** Top of the (possibly multi-row) bottom control block - panels above must not draw past it.
     *  Computed in {@link #init}, read by {@link #render}: one source of truth. */
    private int controlsTop;

    /** For the live flash on a zone-holder change - see {@link #tick}. */
    private String lastHolderSeen = null;
    private boolean holderInitialized = false;
    private int flashTicks = 0;

    public AdminScreen() {
        super(Component.literal("War Admin"));
    }

    @Override
    protected void init() {
        requestSnapshot();

        // Measure how tall the (possibly wrapped) control block needs to be at this width, anchor it
        // to the bottom edge, and reserve exactly that much for the panels above via controlsTop.
        // Fixed x positions were used here once and silently overlapped at small GUI scales.
        int totalHeight = layoutControls(0, false);
        int startY = height - totalHeight - 6;
        layoutControls(startY, true);
        controlsTop = startY - 6;

        // Ticket cap editor, tucked in the corner clear of the centered title/status text.
        ticketCapBox = new EditBox(font, width - 20 - 96, 8, 40, 16, Component.literal("Cap"));
        ticketCapBox.setMaxLength(6);
        ticketCapBox.setFilter(s -> s.isEmpty() || s.chars().allMatch(Character::isDigit));
        ClientboundAdminSnapshotPayload existing = ClientAdminCache.snapshot();
        ticketCapBox.setValue(existing == null ? "" : Integer.toString(existing.ticketCap()));
        addRenderableWidget(ticketCapBox);
        addRenderableWidget(Button.builder(Component.literal("Set Cap"), b -> {
                    try {
                        PacketDistributor.sendToServer(
                                new ServerboundAdminSetTicketCapPayload(Integer.parseInt(ticketCapBox.getValue().trim())));
                    } catch (NumberFormatException ignored) {
                        // empty/invalid entry - just don't send
                    }
                })
                .bounds(width - 20 - 52, 6, 52, 20).build());
    }

    /**
     * Places (or, with {@code createWidgets = false}, just measures) the bottom controls as two rows,
     * each a left-anchored cluster + centered item + right-anchored item.
     *
     * <p>{@link #planRow} places left and right unconditionally and squeezes the center item into the
     * room actually left between them - centered in THAT gap, not dead-centre on screen. Demanding
     * true centering needed ~600px before anything shared a row, and dropped all three to separate
     * rows below that.</p>
     *
     * @return the Y just past the bottom of the last row placed
     */
    private int layoutControls(int startY, boolean createWidgets) {
        int rowH = CONTROL_ROW_HEIGHT;
        int gap = CONTROL_GAP;
        int leftX = 20;
        int rightEdge = width - 20;
        int y = startY;

        // Row 1: [Start 5m][Start 10m][Start 20m] left | Teams & Squads centered | Stop War right.
        int[] plan1 = planRow(70 * 3 + gap * 2, 120, 90);
        int x = leftX;
        for (int minutes : START_PRESETS_MIN) {
            if (createWidgets) {
                addRenderableWidget(Button.builder(Component.literal("Start " + minutes + "m"),
                                b -> PacketDistributor.sendToServer(new ServerboundAdminStartWarPayload(minutes)))
                        .bounds(x, y, 70, rowH).build());
            }
            x += 70 + gap;
        }
        if (createWidgets) {
            addRenderableWidget(Button.builder(Component.literal("Teams & Squads"),
                            b -> Minecraft.getInstance().setScreen(new AdminTeamsScreen()))
                    .bounds(plan1[2], y + plan1[0] * (rowH + gap), 120, rowH).build());
        }
        if (createWidgets) {
            addRenderableWidget(Button.builder(Component.literal("Stop War").withStyle(ChatFormatting.RED),
                            b -> PacketDistributor.sendToServer(new ServerboundAdminEndWarPayload()))
                    .bounds(rightEdge - 90, y + plan1[1] * (rowH + gap), 90, rowH).build());
        }
        y += (Math.max(plan1[0], plan1[1]) + 1) * (rowH + gap);

        // Row 2: [Kit Library][Scarce Weapons][Kit Budgets] left | -5m/+5m/TP to Zone/TP All to Bases
        // centered | Reset Tickets right.
        int centerWidth2 = 36 + gap + 36 + gap + 84 + gap + 110;
        int[] plan2 = planRow(90 + gap + 110 + gap + 100, centerWidth2, 100);
        int x2 = leftX;
        if (createWidgets) {
            addRenderableWidget(Button.builder(Component.literal("Kit Library"),
                            b -> Minecraft.getInstance().setScreen(new AdminKitsScreen()))
                    .bounds(x2, y, 90, rowH).build());
        }
        x2 += 90 + gap;
        if (createWidgets) {
            addRenderableWidget(Button.builder(Component.literal("Scarce Weapons"),
                            b -> Minecraft.getInstance().setScreen(new AdminScarceScreen()))
                    .bounds(x2, y, 110, rowH).build());
        }
        x2 += 110 + gap;
        if (createWidgets) {
            addRenderableWidget(Button.builder(Component.literal("Kit Budgets"),
                            b -> Minecraft.getInstance().setScreen(new AdminBudgetScreen()))
                    .bounds(x2, y, 100, rowH).build());
        }
        int cy = y + plan2[0] * (rowH + gap);
        int cx = plan2[2];
        if (createWidgets) {
            addRenderableWidget(Button.builder(Component.literal("-5m"),
                            b -> PacketDistributor.sendToServer(new ServerboundAdminAdjustTimePayload(-5)))
                    .bounds(cx, cy, 36, rowH).build());
        }
        cx += 36 + gap;
        if (createWidgets) {
            addRenderableWidget(Button.builder(Component.literal("+5m"),
                            b -> PacketDistributor.sendToServer(new ServerboundAdminAdjustTimePayload(5)))
                    .bounds(cx, cy, 36, rowH).build());
        }
        cx += 36 + gap;
        if (createWidgets) {
            addRenderableWidget(Button.builder(Component.literal("TP to Zone"),
                            b -> PacketDistributor.sendToServer(new ServerboundAdminTeleportToZonePayload()))
                    .bounds(cx, cy, 84, rowH).build());
        }
        cx += 84 + gap;
        if (createWidgets) {
            // Sends every online player to their own team's base (Teams & Squads -> Base sets those).
            addRenderableWidget(Button.builder(Component.literal("TP All to Bases"),
                            b -> PacketDistributor.sendToServer(new ServerboundAdminTeleportAllToBasesPayload()))
                    .bounds(cx, cy, 110, rowH).build());
        }
        if (createWidgets) {
            addRenderableWidget(Button.builder(Component.literal("Reset Tickets"),
                            b -> PacketDistributor.sendToServer(new ServerboundAdminResetTicketsPayload()))
                    .bounds(rightEdge - 100, y + plan2[1] * (rowH + gap), 100, rowH).build());
        }
        y += (Math.max(plan2[0], plan2[1]) + 1) * (rowH + gap);

        return y - gap;
    }

    /**
     * @return {centerRowOffset, rightRowOffset, centerX} - how many rows below the left cluster the
     * center and right items each need (0 = same row), and the X to draw the center item at.
     */
    private int[] planRow(int leftWidth, int centerWidth, int rightWidth) {
        int gap = CONTROL_GAP;
        int leftEnd = 20 + leftWidth;
        int rightStart = (width - 20) - rightWidth;

        if (leftEnd + gap > rightStart - gap) {
            // Left and right themselves collide - very narrow window. All three get their own row.
            return new int[]{1, 2, width / 2 - centerWidth / 2};
        }
        int gapAvailable = (rightStart - gap) - (leftEnd + gap);
        if (gapAvailable >= centerWidth) {
            // Common case: centered in the gap between the anchored groups, not on the whole screen.
            int centerX = (leftEnd + gap) + (gapAvailable - centerWidth) / 2;
            return new int[]{0, 0, centerX};
        }
        // Left and right coexist, but nothing fits between - only the center item drops a row.
        return new int[]{1, 0, width / 2 - centerWidth / 2};
    }

    @Override
    public void tick() {
        if (++pollTicks >= POLL_INTERVAL_TICKS) {
            pollTicks = 0;
            requestSnapshot();
        }

        ClientboundAdminSnapshotPayload snap = ClientAdminCache.snapshot();
        if (snap != null) {
            String holder = snap.zoneBundle().status().holderTeam();
            if (!holderInitialized) {
                holderInitialized = true;
                lastHolderSeen = holder;
            } else if (!Objects.equals(holder, lastHolderSeen)) {
                lastHolderSeen = holder;
                flashTicks = 40; // ~2s at 20 ticks/s
            }
        }
        if (flashTicks > 0) {
            flashTicks--;
        }
    }

    private void requestSnapshot() {
        PacketDistributor.sendToServer(new ServerboundRequestAdminSnapshotPayload());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0) {
            historyScroll = Math.max(0, historyScroll - (int) Math.signum(scrollY));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 12, PickerLayout.TITLE_COLOR);

        ClientboundAdminSnapshotPayload snap = ClientAdminCache.snapshot();
        if (snap == null) {
            graphics.drawCenteredString(font,
                    Component.literal("Loading...").withStyle(ChatFormatting.GRAY),
                    width / 2, height / 2, PickerLayout.HINT_COLOR);
            return;
        }

        drawStatus(graphics, snap, 30);

        int panelTop = 46;
        int panelBottom = controlsTop; // wherever the (possibly wrapped) bottom control block starts
        int midX = width / 2;

        drawTeams(graphics, snap, 20, panelTop, midX - 6, panelBottom);
        int zoneBottom = panelTop + 88;
        drawZone(graphics, snap, midX + 6, panelTop, width - 20, zoneBottom);
        drawHistory(graphics, snap, midX + 6, zoneBottom + 6, width - 20, panelBottom);
    }

    private void drawStatus(GuiGraphics graphics, ClientboundAdminSnapshotPayload snap, int y) {
        Component status = snap.warActive()
                ? Component.literal("WAR RUNNING - " + formatMillis(snap.timeLeftMillis())
                        + " left  -  first to " + snap.ticketCap() + " tickets")
                        .withStyle(ChatFormatting.GOLD)
                : Component.literal("NO WAR RUNNING").withStyle(ChatFormatting.GRAY);
        graphics.drawCenteredString(font, status, width / 2, y, 0xFFFFFFFF);
    }

    private void drawTeams(GuiGraphics graphics, ClientboundAdminSnapshotPayload snap,
                           int left, int top, int right, int bottom) {
        drawPanel(graphics, left, top, right, bottom, "Teams");
        int y = top + 16;
        if (snap.teams().isEmpty()) {
            graphics.drawString(font, "(no scoreboard teams yet - /team add)", left + 6, y, PickerLayout.HINT_COLOR);
            return;
        }
        for (AdminTeamInfo t : snap.teams()) {
            if (y > bottom - LINE_HEIGHT) {
                graphics.drawString(font, "...", left + 6, y, PickerLayout.HINT_COLOR);
                break;
            }
            graphics.fill(left + 6, y + 1, left + 14, y + 9, t.colorArgb());
            graphics.drawString(font, t.team() + "  -  " + t.tickets() + " tickets  ("
                    + t.onlinePlayers().size() + " online)", left + 18, y, 0xFFFFFFFF);
            y += LINE_HEIGHT;

            String names = t.onlinePlayers().isEmpty() ? "(nobody online)" : String.join(", ", t.onlinePlayers());
            for (FormattedCharSequence line : wrap(names, right - left - 24)) {
                if (y > bottom - LINE_HEIGHT) {
                    break;
                }
                graphics.drawString(font, line, left + 18, y, PickerLayout.HINT_COLOR);
                y += LINE_HEIGHT;
            }
            y += 4;
        }
    }

    private void drawZone(GuiGraphics graphics, ClientboundAdminSnapshotPayload snap,
                          int left, int top, int right, int bottom) {
        drawPanel(graphics, left, top, right, bottom, "Capture Zone");
        int y = top + 16;
        AdminZone zone = snap.zoneBundle().zone();
        if (!zone.hasZone()) {
            graphics.drawString(font, "Not set - /war setzone", left + 6, y, PickerLayout.HINT_COLOR);
            return;
        }
        graphics.drawString(font, zone.x() + " " + zone.y() + " " + zone.z() + "   r=" + zone.radius()
                + "   (" + zone.dim() + ")", left + 6, y, PickerLayout.HINT_COLOR);
        y += LINE_HEIGHT;

        AdminZoneStatus status = snap.zoneBundle().status();
        String line;
        int color;
        if (status.contested()) {
            line = "CONTESTED - " + (status.holderPlayers().isEmpty() ? "" : String.join(", ", status.holderPlayers()));
            color = 0xFFC45A5A;
        } else if (!status.holderTeam().isEmpty()) {
            line = "Held by " + status.holderTeam() + " - " + String.join(", ", status.holderPlayers());
            color = 0xFF5AC46A;
        } else {
            line = "Empty - nobody inside";
            color = PickerLayout.HINT_COLOR;
        }

        // Blinking highlight on a holder change, so an admin glancing at the panel notices.
        if (flashTicks > 0 && (flashTicks / 4) % 2 == 0) {
            graphics.fill(left + 2, y - 1, right - 2, y + LINE_HEIGHT - 2, 0x55FFFF55);
        }
        for (FormattedCharSequence l : wrap(line, right - left - 12)) {
            if (y > bottom - LINE_HEIGHT) {
                break;
            }
            graphics.drawString(font, l, left + 6, y, color);
            y += LINE_HEIGHT;
        }
    }

    private void drawHistory(GuiGraphics graphics, ClientboundAdminSnapshotPayload snap,
                             int left, int top, int right, int bottom) {
        drawPanel(graphics, left, top, right, bottom, "History (scroll to browse)");
        List<AdminHistoryEntry> history = snap.history();
        int listTop = top + 16;
        if (history.isEmpty()) {
            graphics.drawString(font, "(no captures yet)", left + 6, listTop, PickerLayout.HINT_COLOR);
            return;
        }

        int visibleLines = Math.max(1, (bottom - listTop) / LINE_HEIGHT);
        int maxScroll = Math.max(0, history.size() - visibleLines);
        historyScroll = Math.min(historyScroll, maxScroll);

        graphics.enableScissor(left, listTop, right, bottom);
        int y = listTop;
        int startIndex = history.size() - 1 - historyScroll;
        for (int i = startIndex; i >= 0 && y < bottom; i--) {
            AdminHistoryEntry e = history.get(i);
            String time = TIME_FMT.format(Instant.ofEpochMilli(e.epochMillis()).atZone(ZoneId.systemDefault()));
            String who = e.team().isEmpty() ? "vacated / contested" : e.team().toUpperCase(Locale.ROOT) + " took it";
            String names = e.players().isEmpty() ? "" : "  (" + String.join(", ", e.players()) + ")";
            graphics.drawString(font, "[" + time + "] " + who + names, left + 6, y, PickerLayout.HINT_COLOR);
            y += LINE_HEIGHT;
        }
        graphics.disableScissor();
    }

    private void drawPanel(GuiGraphics graphics, int left, int top, int right, int bottom, String heading) {
        graphics.fill(left, top, right, bottom, PickerLayout.PANEL_BG);
        graphics.renderOutline(left, top, right - left, bottom - top, PickerLayout.PANEL_BORDER);
        graphics.drawString(font, heading, left + 6, top + 4, 0xFFFFFFFF);
    }

    private List<FormattedCharSequence> wrap(String text, int maxWidth) {
        return font.split(Component.literal(text), Math.max(20, maxWidth));
    }

    private static String formatMillis(long millis) {
        long secs = Math.max(0L, millis / 1000L);
        return (secs / 60L) + ":" + String.format(Locale.ROOT, "%02d", secs % 60L);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
