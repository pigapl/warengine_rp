package com.pigapl.warengine.client.gui;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Grid maths and the panel backdrop shared by both pickers. Card size adapts down until the grid
 * fits - kit count is admin-driven and GUI scale varies, so a fixed size loses the bottom row.
 */
public final class PickerLayout {

    public static final int PANEL_BG = 0xD8101014;
    public static final int PANEL_BORDER = 0xFF32323C;
    public static final int TITLE_COLOR = 0xFFFFFFFF;
    public static final int HINT_COLOR = 0xFF8A8A96;

    private static final int GAP = 8;
    private static final int TOP_RESERVED = 44;    // title
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
