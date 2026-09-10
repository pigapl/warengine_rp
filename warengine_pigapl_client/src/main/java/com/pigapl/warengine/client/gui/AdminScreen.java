package com.pigapl.warengine.client.gui;

import com.pigapl.warengine.network.AdminHistoryEntry;
import com.pigapl.warengine.network.AdminTeamInfo;
import com.pigapl.warengine.network.AdminPointInfo;
import com.pigapl.warengine.network.ClientboundAdminSnapshotPayload;
import com.pigapl.warengine.network.ServerboundAdminAdjustTimePayload;
import com.pigapl.warengine.network.ServerboundAdminEndWarPayload;
import com.pigapl.warengine.network.ServerboundAdminResetTicketsPayload;
import com.pigapl.warengine.network.ServerboundAdminSetTicketCapPayload;
import com.pigapl.warengine.network.ServerboundAdminStartWarPayload;
import com.pigapl.warengine.network.ServerboundAdminTeleportAllToBasesPayload;
import com.pigapl.warengine.network.ServerboundAdminTeleportToPointPayload;
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

public final class AdminScreen extends Screen {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT);
    private static final int[] START_PRESETS_MIN = {5, 10, 20};
    private static final int POLL_INTERVAL_TICKS = 20;
    private static final int LINE_HEIGHT = 10;

    private static final int POINTS_PANEL_TOP = 46;
    private static final int POINT_ROW_HEIGHT = 22;
    private static final int POINT_TP_WIDTH = 26;

    private static final int CONTROL_ROW_HEIGHT = 20;
    private static final int CONTROL_GAP = 4;

    private int pollTicks = 0;
    private int historyScroll = 0;

    private EditBox ticketCapBox;

    private int controlsTop;

    private String lastOwnersSeen = null;
    private String lastPointIdsSeen = null;
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
                    }
                })
                .bounds(width - 20 - 52, 6, 52, 20).build());

        addPointButtons();
    }

    private void addPointButtons() {
        ClientboundAdminSnapshotPayload snap = ClientAdminCache.snapshot();
        if (snap == null || snap.points().isEmpty()) {
            return;
        }
        int right = width - 20;
        forEachVisiblePointRow(snap.points(), POINTS_PANEL_TOP, pointsBottom(), (point, y) ->
                addRenderableWidget(Button.builder(Component.literal("TP"),
                                b -> PacketDistributor.sendToServer(
                                        new ServerboundAdminTeleportToPointPayload(point.loc().id())))
                        .bounds(pointButtonsX(right), y - 2, POINT_TP_WIDTH, 18).build()));
    }

    /**
     * Places, or with {@code createWidgets = false} just measures, the bottom controls.
     * {@link #planRow} centers the middle item in the gap between the anchored groups, not on screen -
     * true centering needed ~600px before anything shared a row.
     */
    private int layoutControls(int startY, boolean createWidgets) {
        int rowH = CONTROL_ROW_HEIGHT;
        int gap = CONTROL_GAP;
        int leftX = 20;
        int rightEdge = width - 20;
        int y = startY;

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

        int centerWidth2 = 36 + gap + 36 + gap + 110;
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

    private int[] planRow(int leftWidth, int centerWidth, int rightWidth) {
        int gap = CONTROL_GAP;
        int leftEnd = 20 + leftWidth;
        int rightStart = (width - 20) - rightWidth;

        if (leftEnd + gap > rightStart - gap) {
            return new int[]{1, 2, width / 2 - centerWidth / 2};
        }
        int gapAvailable = (rightStart - gap) - (leftEnd + gap);
        if (gapAvailable >= centerWidth) {
            int centerX = (leftEnd + gap) + (gapAvailable - centerWidth) / 2;
            return new int[]{0, 0, centerX};
        }
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
            StringBuilder owners = new StringBuilder();
            for (AdminPointInfo p : snap.points()) {
                owners.append(p.loc().id()).append('=').append(p.status().owner()).append(';');
            }
            String signature = owners.toString();
            if (lastOwnersSeen != null && !Objects.equals(signature, lastOwnersSeen)) {
                flashTicks = 40;
            }
            lastOwnersSeen = signature;

            // Only the SET of points forces a rebuild (a point added or removed needs its own TP
            // button); owner and progress changes are drawn live and must not tear down widgets.
            StringBuilder ids = new StringBuilder();
            for (AdminPointInfo p : snap.points()) {
                ids.append(p.loc().id()).append(';');
            }
            String idSignature = ids.toString();
            if (lastPointIdsSeen != null && !idSignature.equals(lastPointIdsSeen)) {
                // Poll-triggered rebuildWidgets() wipes an EditBox mid-type, so save and restore it.
                String cap = ticketCapBox.getValue();
                boolean focused = ticketCapBox.isFocused();
                rebuildWidgets();
                ticketCapBox.setValue(cap);
                if (focused) {
                    setFocused(ticketCapBox);
                }
            }
            lastPointIdsSeen = idSignature;
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

        int panelTop = POINTS_PANEL_TOP;
        int panelBottom = controlsTop;
        int midX = width / 2;

        drawTeams(graphics, snap, 20, panelTop, midX - 6, panelBottom);
        drawPoints(graphics, snap, midX + 6, panelTop, width - 20, pointsBottom());
        drawHistory(graphics, snap, midX + 6, pointsBottom() + 6, width - 20, panelBottom);
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

    private int pointsBottom() {
        return POINTS_PANEL_TOP + 88;
    }

    /**
     * Called from BOTH {@link #init} (to place TP buttons) and {@link #drawPoints} (to draw text).
     * Every caller must pass the SAME arguments - that is the half this screen has gotten wrong before.
     */
    private void forEachVisiblePointRow(List<AdminPointInfo> points, int top, int bottom,
                                        java.util.function.ObjIntConsumer<AdminPointInfo> row) {
        int y = top + 16;
        for (AdminPointInfo point : points) {
            if (y + POINT_ROW_HEIGHT > bottom) {
                return;
            }
            row.accept(point, y);
            y += POINT_ROW_HEIGHT;
        }
    }

    private void drawPoints(GuiGraphics graphics, ClientboundAdminSnapshotPayload snap,
                            int left, int top, int right, int bottom) {
        drawPanel(graphics, left, top, right, bottom, "Capture Points");
        List<AdminPointInfo> points = snap.points();
        if (points.isEmpty()) {
            graphics.drawString(font, "None - /war point add <id>", left + 6, top + 16,
                    PickerLayout.HINT_COLOR);
            return;
        }

        forEachVisiblePointRow(points, top, bottom, (point, y) -> {
            String owner = point.status().owner();
            String head = point.loc().id() + "  " + (owner.isEmpty() ? "neutral" : owner)
                    + "   " + point.loc().x() + " " + point.loc().y() + " " + point.loc().z()
                    + " r=" + point.loc().radius();
            graphics.drawString(font,
                    font.plainSubstrByWidth(head, pointButtonsX(right) - (left + 6) - 4),
                    left + 6, y, owner.isEmpty() ? PickerLayout.HINT_COLOR : 0xFFFFFFFF);

            String line;
            int color;
            if (point.status().contested()) {
                line = "CONTESTED - " + String.join(", ", point.status().occupants());
                color = 0xFFC45A5A;
            } else if (!point.status().capturing().isEmpty()) {
                line = point.status().capturing() + " capturing  " + point.status().progress()
                        + "/" + point.status().captureTotal()
                        + "  (" + String.join(", ", point.status().occupants()) + ")";
                color = 0xFFD8B24A;
            } else if (!point.status().occupants().isEmpty()) {
                line = "held - " + String.join(", ", point.status().occupants());
                color = 0xFF5AC46A;
            } else {
                line = "nobody inside";
                color = PickerLayout.HINT_COLOR;
            }

            if (flashTicks > 0 && (flashTicks / 4) % 2 == 0) {
                graphics.fill(left + 2, y - 1, right - 2, y + POINT_ROW_HEIGHT - 3, 0x55FFFF55);
            }
            graphics.drawString(font, font.plainSubstrByWidth(line, right - left - 12),
                    left + 6, y + LINE_HEIGHT, color);
        });
    }

    private int pointButtonsX(int right) {
        return right - 6 - POINT_TP_WIDTH;
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
            String point = e.point().isEmpty() ? "" : e.point() + " ";
            String who = e.team().isEmpty() ? "went neutral" : "taken by " + e.team().toUpperCase(Locale.ROOT);
            String names = e.players().isEmpty() ? "" : "  (" + String.join(", ", e.players()) + ")";
            graphics.drawString(font, font.plainSubstrByWidth("[" + time + "] " + point + who + names,
                    right - left - 12), left + 6, y, PickerLayout.HINT_COLOR);
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
