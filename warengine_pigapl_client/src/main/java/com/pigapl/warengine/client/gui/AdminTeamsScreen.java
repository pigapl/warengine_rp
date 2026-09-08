package com.pigapl.warengine.client.gui;

import com.pigapl.warengine.network.AdminKitInfo;
import com.pigapl.warengine.network.AdminPlayerInfo;
import com.pigapl.warengine.network.AdminSquadDetail;
import com.pigapl.warengine.network.AdminTeamDetail;
import com.pigapl.warengine.network.ClientboundAdminKitsSnapshotPayload;
import com.pigapl.warengine.network.ClientboundAdminTeamsSnapshotPayload;
import com.pigapl.warengine.network.ServerboundAdminClearTeamBasePayload;
import com.pigapl.warengine.network.ServerboundAdminDeleteTeamPayload;
import com.pigapl.warengine.network.ServerboundAdminForceKitPayload;
import com.pigapl.warengine.network.ServerboundAdminForceResupplyPayload;
import com.pigapl.warengine.network.ServerboundAdminKickSquadMemberPayload;
import com.pigapl.warengine.network.ServerboundAdminMoveToTeamPayload;
import com.pigapl.warengine.network.ServerboundAdminRestoreTeamsPayload;
import com.pigapl.warengine.network.ServerboundAdminSetTeamBasePayload;
import com.pigapl.warengine.network.ServerboundAdminTeleportToPlayerPayload;
import com.pigapl.warengine.network.ServerboundAdminTeleportToTeamBasePayload;
import com.pigapl.warengine.network.ServerboundAdminUpsertTeamPayload;
import com.pigapl.warengine.network.ServerboundRequestAdminKitsSnapshotPayload;
import com.pigapl.warengine.network.ServerboundRequestAdminTeamsSnapshotPayload;
import com.pigapl.warengine.network.client.ClientAdminCache;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Team + squad oversight: every team (color, roster, create/delete/recolor, base) and every squad
 * (full roster including offline members, plus per-member teleport/force-kit/resupply/kick).
 *
 * <p>Reached from {@link AdminScreen}'s "Teams &amp; Squads". Same polling design - the server has no
 * idea this screen is open. Also polls the kit library, so the per-member "Kit" action already has a
 * populated list when clicked.</p>
 */
public final class AdminTeamsScreen extends Screen {

    private static final int POLL_INTERVAL_TICKS = 20;
    private static final int ROW_HEIGHT = 14;
    private static final String[] COLOR_CYCLE = {
            "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray",
            "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white"
    };

    private int pollTicks = 0;
    private int lastSeenRevision = -1;
    private int teamsScroll = 0;
    private int squadsScroll = 0;

    private EditBox newTeamBox;

    private int teamsTop;
    private int teamsBottom;
    private int squadsTop;
    private int squadsBottom;
    private int left;
    private int right;

    public AdminTeamsScreen() {
        super(Component.literal("Teams & Squads"));
    }

    @Override
    protected void init() {
        requestSnapshots();
        lastSeenRevision = ClientAdminCache.revision();

        left = 20;
        right = width - 20;

        addRenderableWidget(Button.builder(Component.literal("Back to Event"),
                        b -> Minecraft.getInstance().setScreen(new AdminScreen()))
                .bounds(left, height - 26, 110, 20).build());

        // FlowLayout, not fixed x positions: these overlapped below ~500px wide when one was
        // left-anchored and the other right-anchored. teamsTop is read from wherever the row ended up.
        FlowLayout topFlow = new FlowLayout(left, 24, right, 18, 4);
        int[] boxPos = topFlow.next(120);
        newTeamBox = new EditBox(font, boxPos[0], boxPos[1], 120, 18, Component.literal("New team id"));
        newTeamBox.setMaxLength(24);
        newTeamBox.setHint(Component.literal("new team id").withStyle(ChatFormatting.DARK_GRAY));
        addRenderableWidget(newTeamBox);
        int[] createPos = topFlow.next(90);
        addRenderableWidget(Button.builder(Component.literal("Create Team"), b -> {
                    String id = newTeamBox.getValue().trim();
                    if (!id.isEmpty()) {
                        PacketDistributor.sendToServer(new ServerboundAdminUpsertTeamPayload(id, ""));
                        newTeamBox.setValue("");
                    }
                })
                .bounds(createPos[0], createPos[1], 90, 18).build());
        int[] restorePos = topFlow.next(100);
        addRenderableWidget(Button.builder(Component.literal("Restore Teams"),
                        b -> PacketDistributor.sendToServer(new ServerboundAdminRestoreTeamsPayload()))
                .bounds(restorePos[0], restorePos[1], 100, 18).build());

        teamsTop = topFlow.bottom() + 6;
        teamsBottom = teamsTop + estimateTeamsHeight();
        squadsTop = teamsBottom + 8;
        squadsBottom = height - 34;

        ClientboundAdminTeamsSnapshotPayload snap = ClientAdminCache.teamsSnapshot();
        List<AdminTeamDetail> teams = snap == null ? List.of() : snap.teams();
        List<AdminSquadDetail> squads = snap == null ? List.of() : snap.squads();
        clampTeamsScroll(teams);
        clampSquadsScroll(squads);

        // +16 matches the heading reserved by drawPanel()/drawTeamsPanel(). These buttons MUST start
        // at the same row as render()'s text or they drift out of alignment - this was a real bug.
        forEachVisibleTeamRow(teams, teamsTop + 16, teamsBottom, (y, team, member) -> {
            if (member == null) {
                int bx = teamHeaderButtonsX();
                // "Base" sets it to where the ADMIN is standing (the server reads the position off the
                // sender), so walk there first. "B>" teleports you to it; "Clr" removes it.
                addRenderableWidget(Button.builder(Component.literal("Base"),
                                b -> PacketDistributor.sendToServer(new ServerboundAdminSetTeamBasePayload(team.team())))
                        .bounds(bx, y - 1, 36, ROW_HEIGHT - 2).build());
                Button toBase = Button.builder(Component.literal("B>"),
                                b -> PacketDistributor.sendToServer(
                                        new ServerboundAdminTeleportToTeamBasePayload(team.team())))
                        .bounds(bx + 38, y - 1, 22, ROW_HEIGHT - 2).build();
                toBase.active = !team.baseSummary().isEmpty();
                addRenderableWidget(toBase);
                Button clearBase = Button.builder(Component.literal("Clr"),
                                b -> PacketDistributor.sendToServer(
                                        new ServerboundAdminClearTeamBasePayload(team.team())))
                        .bounds(bx + 62, y - 1, 26, ROW_HEIGHT - 2).build();
                clearBase.active = !team.baseSummary().isEmpty();
                addRenderableWidget(clearBase);
                addRenderableWidget(Button.builder(Component.literal("Color"),
                                b -> PacketDistributor.sendToServer(
                                        new ServerboundAdminUpsertTeamPayload(team.team(), nextColor(team.colorArgb()))))
                        .bounds(right - 90, y - 1, 44, ROW_HEIGHT - 2).build());
                addRenderableWidget(Button.builder(Component.literal("Del").withStyle(ChatFormatting.RED),
                                b -> PacketDistributor.sendToServer(new ServerboundAdminDeleteTeamPayload(team.team())))
                        .bounds(right - 44, y - 1, 44, ROW_HEIGHT - 2).build());
            } else {
                addRenderableWidget(Button.builder(Component.literal("TP"),
                                b -> PacketDistributor.sendToServer(new ServerboundAdminTeleportToPlayerPayload(member.uuid())))
                        .bounds(teamMemberButtonsX(), y - 1, 36, ROW_HEIGHT - 2).build());
                addRenderableWidget(Button.builder(Component.literal("Move"), b -> openTeamPicker(member, teams))
                        .bounds(right - 46, y - 1, 46, ROW_HEIGHT - 2).build());
            }
        });

        forEachVisibleSquadRow(squads, squadsTop + 16, squadsBottom, (y, squad, member) -> {
            if (member == null) {
                addRenderableWidget(Button.builder(Component.literal("Edit"),
                                b -> Minecraft.getInstance().setScreen(new EditSquadScreen(squad.id())))
                        .bounds(squadHeaderButtonsX(), y - 1, 40, ROW_HEIGHT - 2).build());
                return;
            }
            int kickX = right - 32;
            int supX = kickX - 2 - 26;
            int kitX = supX - 2 - 26;
            int tpX = squadMemberButtonsX();
            addRenderableWidget(Button.builder(Component.literal("TP"),
                            b -> PacketDistributor.sendToServer(new ServerboundAdminTeleportToPlayerPayload(member.uuid())))
                    .bounds(tpX, y - 1, 20, ROW_HEIGHT - 2).build());
            addRenderableWidget(Button.builder(Component.literal("Kit"), b -> openKitPicker(member))
                    .bounds(kitX, y - 1, 26, ROW_HEIGHT - 2).build());
            addRenderableWidget(Button.builder(Component.literal("Sup"),
                            b -> PacketDistributor.sendToServer(new ServerboundAdminForceResupplyPayload(member.uuid())))
                    .bounds(supX, y - 1, 26, ROW_HEIGHT - 2).build());
            addRenderableWidget(Button.builder(Component.literal("Kick").withStyle(ChatFormatting.RED),
                            b -> PacketDistributor.sendToServer(new ServerboundAdminKickSquadMemberPayload(member.uuid())))
                    .bounds(kickX, y - 1, 32, ROW_HEIGHT - 2).build());
        });
    }

    // Button-group start X per row kind, called from BOTH init() (placement) and the draw* methods
    // (dotted-line end), so the two can never disagree about where a row's buttons begin.
    /** Base(36) + B&gt;(22) + Clr(26) with 2px gaps, then Color(44) and Del(44) pinned to the right edge. */
    private int teamHeaderButtonsX() {
        return right - 90 - 2 - (36 + 2 + 22 + 2 + 26);
    }

    private int teamMemberButtonsX() {
        return right - 84;
    }

    private int squadHeaderButtonsX() {
        return right - 40;
    }

    private int squadMemberButtonsX() {
        int kickX = right - 32;
        int supX = kickX - 2 - 26;
        int kitX = supX - 2 - 26;
        return kitX - 2 - 20;
    }

    private void openTeamPicker(AdminPlayerInfo member, List<AdminTeamDetail> teams) {
        List<AdminPickScreen.Entry> entries = new ArrayList<>();
        for (AdminTeamDetail t : teams) {
            entries.add(new AdminPickScreen.Entry(t.team(), t.displayName() + " [" + t.team() + "]"));
        }
        Minecraft.getInstance().setScreen(new AdminPickScreen("Move to Team", entries,
                teamId -> PacketDistributor.sendToServer(new ServerboundAdminMoveToTeamPayload(member.uuid(), teamId)),
                this));
    }

    private void openKitPicker(AdminPlayerInfo member) {
        ClientboundAdminKitsSnapshotPayload kitsSnap = ClientAdminCache.kitsSnapshot();
        List<AdminPickScreen.Entry> entries = new ArrayList<>();
        if (kitsSnap != null) {
            for (AdminKitInfo kit : kitsSnap.kits()) {
                entries.add(new AdminPickScreen.Entry(kit.id(), kit.displayName() + " [" + kit.id() + "]"));
            }
        }
        Minecraft.getInstance().setScreen(new AdminPickScreen("Set Kit", entries,
                kitId -> PacketDistributor.sendToServer(new ServerboundAdminForceKitPayload(member.uuid(), kitId)),
                this));
    }

    /** Next color name after the one matching {@code currentArgb} in {@link #COLOR_CYCLE}, wrapping around. */
    private static String nextColor(int currentArgb) {
        int idx = -1;
        for (int i = 0; i < COLOR_CYCLE.length; i++) {
            if (argbFor(COLOR_CYCLE[i]) == currentArgb) {
                idx = i;
                break;
            }
        }
        return COLOR_CYCLE[(idx + 1) % COLOR_CYCLE.length];
    }

    private static int argbFor(String colorName) {
        ChatFormatting f = ChatFormatting.getByName(colorName);
        Integer rgb = f == null ? null : f.getColor();
        return rgb == null ? 0xFF8A8A96 : (0xFF000000 | rgb);
    }

    /**
     * Panel height for the teams block, so the squads panel below never overlaps (capped; scroll
     * handles the rest). Must include the 16px heading the rows start below - omitting it bled the
     * teams panel into the squads panel with as few as 3 teams x 2 players.
     */
    private int estimateTeamsHeight() {
        ClientboundAdminTeamsSnapshotPayload snap = ClientAdminCache.teamsSnapshot();
        if (snap == null || snap.teams().isEmpty()) {
            return 40;
        }
        int totalRows = 0;
        for (AdminTeamDetail t : snap.teams()) {
            totalRows += 1 + t.members().size();
        }
        return Math.min(150, Math.max(30, totalRows * ROW_HEIGHT + 16 + 8));
    }

    @Override
    public void tick() {
        if (++pollTicks >= POLL_INTERVAL_TICKS) {
            pollTicks = 0;
            requestSnapshots();
        }
        if (lastSeenRevision != ClientAdminCache.revision()) {
            lastSeenRevision = ClientAdminCache.revision();
            // rebuildWidgets() fires on every polled reply (~1/s) and recreates every widget, which
            // would wipe whatever was mid-typing in newTeamBox. Save and restore across the rebuild.
            String typedTeamId = newTeamBox == null ? "" : newTeamBox.getValue();
            boolean wasFocused = newTeamBox != null && newTeamBox.isFocused();
            rebuildWidgets();
            if (newTeamBox != null) {
                newTeamBox.setValue(typedTeamId);
                if (wasFocused) {
                    setFocused(newTeamBox);
                }
            }
        }
    }

    private void requestSnapshots() {
        PacketDistributor.sendToServer(new ServerboundRequestAdminTeamsSnapshotPayload());
        PacketDistributor.sendToServer(new ServerboundRequestAdminKitsSnapshotPayload());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0) {
            if (mouseY < teamsBottom) {
                teamsScroll = Math.max(0, teamsScroll - (int) Math.signum(scrollY));
            } else {
                squadsScroll = Math.max(0, squadsScroll - (int) Math.signum(scrollY));
            }
            rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 12, PickerLayout.TITLE_COLOR);

        ClientboundAdminTeamsSnapshotPayload snap = ClientAdminCache.teamsSnapshot();
        if (snap == null) {
            graphics.drawCenteredString(font, Component.literal("Loading...").withStyle(ChatFormatting.GRAY),
                    width / 2, height / 2, PickerLayout.HINT_COLOR);
            return;
        }

        drawTeamsPanel(graphics, snap.teams());
        drawSquadsPanel(graphics, snap.squads());
    }

    private void drawTeamsPanel(GuiGraphics graphics, List<AdminTeamDetail> teams) {
        drawPanel(graphics, left, teamsTop, right, teamsBottom, "Teams (scroll to browse)");
        if (teams.isEmpty()) {
            graphics.drawString(font, "(no scoreboard teams yet - create one above)",
                    left + 6, teamsTop + 16, PickerLayout.HINT_COLOR);
            return;
        }
        int listTop = teamsTop + 16;
        graphics.enableScissor(left, listTop, right, teamsBottom);
        forEachVisibleTeamRow(teams, listTop, teamsBottom, (y, team, member) -> {
            if (member == null) {
                graphics.fill(left + 6, y + 1, left + 14, y + 9, team.colorArgb());
                String base = team.baseSummary().isEmpty() ? "no base" : "base " + team.baseSummary();
                String full = team.displayName() + "  [" + team.team() + "]  -  " + team.members().size()
                        + " online  -  " + base;
                // Clipped to where this row's buttons start - the header carries five of them
                // (Base/B>/Clr/Color/Del), so a long team line would otherwise run underneath.
                String text = font.plainSubstrByWidth(full, Math.max(20, teamHeaderButtonsX() - 4 - (left + 18)));
                graphics.drawString(font, text, left + 18, y, 0xFFFFFFFF);
                drawDottedLine(graphics, left + 18 + font.width(text), teamHeaderButtonsX(), y + 4, PickerLayout.HINT_COLOR);
            } else {
                String text = "  " + member.name();
                graphics.drawString(font, text, left + 18, y, PickerLayout.HINT_COLOR);
                drawDottedLine(graphics, left + 18 + font.width(text), teamMemberButtonsX(), y + 4, 0xFF4A4A54);
            }
        });
        graphics.disableScissor();
    }

    private void drawSquadsPanel(GuiGraphics graphics, List<AdminSquadDetail> squads) {
        drawPanel(graphics, left, squadsTop, right, squadsBottom,
                "Squads (scroll to browse - TP / Kit / Sup / Kick per member)");
        if (squads.isEmpty()) {
            graphics.drawString(font, "(no squads yet)", left + 6, squadsTop + 16, PickerLayout.HINT_COLOR);
            return;
        }
        int listTop = squadsTop + 16;
        graphics.enableScissor(left, listTop, right, squadsBottom);
        forEachVisibleSquadRow(squads, listTop, squadsBottom, (y, squad, member) -> {
            if (member == null) {
                String text = squad.name() + "  [" + squad.team() + "]  " + squad.members().size() + "/" + squad.limit();
                graphics.drawString(font, text, left + 6, y + 2, 0xFFFFFFFF);
                drawDottedLine(graphics, left + 6 + font.width(text), squadHeaderButtonsX(), y + 6, PickerLayout.HINT_COLOR);
            } else {
                String kit = member.kitId().isEmpty() ? "(no kit)" : member.kitId();
                String tag = member.online() ? "" : "  (offline)";
                int color = member.online() ? PickerLayout.HINT_COLOR : 0xFF6A6A72;
                String text = "  " + member.name() + tag + "  -  " + kit;
                graphics.drawString(font, text, left + 6, y + 2, color);
                drawDottedLine(graphics, left + 6 + font.width(text), squadMemberButtonsX(), y + 6, 0xFF4A4A54);
            }
        });
        graphics.disableScissor();
    }

    /** Small dashed connector from where a row's text ends to where its action buttons begin. */
    private void drawDottedLine(GuiGraphics graphics, int x1, int x2, int y, int color) {
        for (int x = x1 + 2; x < x2 - 2; x += 4) {
            graphics.fill(x, y, Math.min(x + 2, x2 - 2), y + 1, color);
        }
    }

    private void drawPanel(GuiGraphics graphics, int left, int top, int right, int bottom, String heading) {
        graphics.fill(left, top, right, bottom, PickerLayout.PANEL_BG);
        graphics.renderOutline(left, top, right - left, bottom - top, PickerLayout.PANEL_BORDER);
        graphics.drawString(font, heading, left + 6, top + 4, 0xFFFFFFFF);
    }

    // ------------------------------------------------------------------ row layout

    @FunctionalInterface
    private interface TeamRowVisitor {
        /** {@code member == null} marks a team's header row. */
        void visit(int y, AdminTeamDetail team, AdminPlayerInfo member);
    }

    @FunctionalInterface
    private interface SquadRowVisitor {
        /** {@code member == null} marks a squad's header row. */
        void visit(int y, AdminSquadDetail squad, AdminPlayerInfo member);
    }

    /**
     * Flattens every team into (header row + one row per ONLINE member), applies {@link #teamsScroll},
     * and visits only rows that fit. Used by BOTH {@link #init} (button placement) and
     * {@link #drawTeamsPanel} (text), so the two can never disagree on where a row is.
     */
    private void forEachVisibleTeamRow(List<AdminTeamDetail> teams, int top, int bottom, TeamRowVisitor visitor) {
        int rowIndex = 0;
        int y = top;
        for (AdminTeamDetail team : teams) {
            if (y >= bottom) {
                return;
            }
            if (rowIndex >= teamsScroll) {
                visitor.visit(y, team, null);
                y += ROW_HEIGHT;
            }
            rowIndex++;
            for (AdminPlayerInfo member : team.members()) {
                if (y >= bottom) {
                    return;
                }
                if (rowIndex >= teamsScroll) {
                    visitor.visit(y, team, member);
                    y += ROW_HEIGHT;
                }
                rowIndex++;
            }
        }
    }

    /** Same idea as {@link #forEachVisibleTeamRow}, for squads (full roster, online or not). */
    private void forEachVisibleSquadRow(List<AdminSquadDetail> squads, int top, int bottom, SquadRowVisitor visitor) {
        int rowIndex = 0;
        int y = top;
        for (AdminSquadDetail squad : squads) {
            if (y >= bottom) {
                return;
            }
            if (rowIndex >= squadsScroll) {
                visitor.visit(y, squad, null);
                y += ROW_HEIGHT;
            }
            rowIndex++;
            for (AdminPlayerInfo member : squad.members()) {
                if (y >= bottom) {
                    return;
                }
                if (rowIndex >= squadsScroll) {
                    visitor.visit(y, squad, member);
                    y += ROW_HEIGHT;
                }
                rowIndex++;
            }
        }
    }

    private void clampTeamsScroll(List<AdminTeamDetail> teams) {
        int totalRows = 0;
        for (AdminTeamDetail t : teams) {
            totalRows += 1 + t.members().size();
        }
        int visibleRows = Math.max(1, (teamsBottom - (teamsTop + 16)) / ROW_HEIGHT);
        teamsScroll = Math.max(0, Math.min(teamsScroll, Math.max(0, totalRows - visibleRows)));
    }

    private void clampSquadsScroll(List<AdminSquadDetail> squads) {
        int totalRows = 0;
        for (AdminSquadDetail squad : squads) {
            totalRows += 1 + squad.members().size();
        }
        int visibleRows = Math.max(1, (squadsBottom - (squadsTop + 16)) / ROW_HEIGHT);
        squadsScroll = Math.max(0, Math.min(squadsScroll, Math.max(0, totalRows - visibleRows)));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
