package com.pigapl.warengine.client.gui;

import com.pigapl.warengine.network.KitCatalogEntry;
import com.pigapl.warengine.network.SquadEntry;
import com.pigapl.warengine.network.client.ClientKitCache;
import com.pigapl.warengine.network.client.ClientSquadCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.scores.PlayerTeam;

/**
 * Grid maths and the panel backdrop shared by both pickers. Card size adapts down until the grid
 * fits - kit count is admin-driven and GUI scale varies, so a fixed size loses the bottom row.
 */
public final class PickerLayout {

    public static final int PANEL_BG = 0xD8101014;
    public static final int PANEL_BORDER = 0xFF32323C;
    public static final int TITLE_COLOR = 0xFFFFFFFF;
    public static final int HINT_COLOR = 0xFF8A8A96;
    public static final int STEP_DONE_COLOR = 0xFF5AC46A;

    public static final int STEP_TEAM = 1;
    public static final int STEP_SQUAD = 2;
    public static final int STEP_KIT = 3;

    private static final int GAP = 8;
    private static final int TOP_RESERVED = 58;    // title + the step breadcrumb
    private static final int BOTTOM_RESERVED = 52; // hint line + the kit screen's Change Team button

    private PickerLayout() {}

    public record Grid(int cardWidth, int cardHeight, int columns, int rows, int originX, int originY) {
        public int xFor(int index) {
            return originX + (index % columns) * (cardWidth + GAP);
        }

        public int yFor(int index) {
            return originY + (index / columns) * (cardHeight + GAP);
        }

        public int totalWidth() {
            return columns * cardWidth + (columns - 1) * GAP;
        }

        public int totalHeight() {
            return rows * cardHeight + (rows - 1) * GAP;
        }
    }

    /** Largest card size (down to a floor) at which {@code count} cards fit, then centred. */
    public static Grid solve(int count, int screenWidth, int screenHeight) {
        int availableWidth = screenWidth - 40;
        int availableHeight = screenHeight - TOP_RESERVED - BOTTOM_RESERVED;

        for (int cardWidth = 96; cardWidth >= 56; cardWidth -= 8) {
            int cardHeight = cardWidth - 8;
            int columns = Math.max(1, Math.min(count, (availableWidth + GAP) / (cardWidth + GAP)));
            int rows = (count + columns - 1) / columns;
            int gridHeight = rows * cardHeight + (rows - 1) * GAP;
            if (gridHeight <= availableHeight) {
                int gridWidth = columns * cardWidth + (columns - 1) * GAP;
                return new Grid(cardWidth, cardHeight, columns, rows,
                        (screenWidth - gridWidth) / 2,
                        TOP_RESERVED + Math.max(0, (availableHeight - gridHeight) / 2));
            }
        }

        // Overflows even at the floor size - start under the title rather than centring off-screen.
        int cardWidth = 56;
        int cardHeight = 48;
        int columns = Math.max(1, Math.min(count, (availableWidth + GAP) / (cardWidth + GAP)));
        int rows = (count + columns - 1) / columns;
        int gridWidth = columns * cardWidth + (columns - 1) * GAP;
        return new Grid(cardWidth, cardHeight, columns, rows,
                (screenWidth - gridWidth) / 2, TOP_RESERVED);
    }

    /**
     * The Team -> Squad -> Kit breadcrumb. Event 1's complaint was that nobody could tell the flow
     * existed, so every picker shows all three steps and what you have already chosen.
     */
    public static void drawSteps(GuiGraphics graphics, Font font, int screenWidth, int activeStep) {
        String[] labels = {
                stepLabel("Team", ownTeamName(), activeStep),
                stepLabel("Squad", ownSquadName(), activeStep),
                stepLabel("Kit", ownKitName(), activeStep)
        };

        int separatorWidth = font.width(" > ");
        int total = 0;
        for (String label : labels) {
            total += font.width(label);
        }
        total += separatorWidth * (labels.length - 1);

        int x = (screenWidth - total) / 2;
        for (int i = 0; i < labels.length; i++) {
            int step = i + 1;
            int color = step == activeStep ? TITLE_COLOR
                    : (isDone(step) ? STEP_DONE_COLOR : HINT_COLOR);
            graphics.drawString(font, labels[i], x, 36, color, false);
            x += font.width(labels[i]);
            if (i < labels.length - 1) {
                graphics.drawString(font, " > ", x, 36, HINT_COLOR, false);
                x += separatorWidth;
            }
        }
    }

    /** Chosen values are shown on the steps behind you; the one you are on stays a plain label. */
    private static String stepLabel(String name, String value, int activeStep) {
        boolean showValue = !value.isEmpty() && !name.equals(stepNameOf(activeStep));
        return showValue ? name + ": " + trim(value) : name;
    }

    private static String stepNameOf(int step) {
        return step == STEP_TEAM ? "Team" : step == STEP_SQUAD ? "Squad" : "Kit";
    }

    private static boolean isDone(int step) {
        return switch (step) {
            case STEP_TEAM -> !ownTeamName().isEmpty();
            case STEP_SQUAD -> !ownSquadName().isEmpty();
            default -> !ownKitName().isEmpty();
        };
    }

    private static String trim(String value) {
        return value.length() <= 14 ? value : value.substring(0, 13) + "...";
    }

    private static String ownTeamName() {
        Minecraft mc = Minecraft.getInstance();
        PlayerTeam team = mc.player == null ? null : (PlayerTeam) mc.player.getTeam();
        return team == null ? "" : team.getName();
    }

    private static String ownSquadName() {
        String id = ClientSquadCache.squadId();
        if (id.isEmpty()) {
            return "";
        }
        for (SquadEntry squad : ClientSquadCache.squads()) {
            if (squad.id().equals(id)) {
                return squad.name();
            }
        }
        return id;   // list not pushed yet - the id is still better than a blank step
    }

    private static String ownKitName() {
        String id = ClientKitCache.kitId();
        if (id.isEmpty()) {
            return "";
        }
        for (KitCatalogEntry kit : ClientKitCache.catalog()) {
            if (kit.id().equals(id)) {
                return kit.displayName();
            }
        }
        return id;
    }

    public static void drawPanel(GuiGraphics graphics, Grid grid) {
        int pad = 12;
        int left = grid.originX() - pad;
        int top = grid.originY() - pad;
        int right = grid.originX() + grid.totalWidth() + pad;
        int bottom = grid.originY() + grid.totalHeight() + pad;
        graphics.fill(left, top, right, bottom, PANEL_BG);
        graphics.renderOutline(left, top, right - left, bottom - top, PANEL_BORDER);
    }
}
