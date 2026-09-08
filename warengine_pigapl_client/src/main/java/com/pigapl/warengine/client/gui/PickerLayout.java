package com.pigapl.warengine.client.gui;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Grid maths and the panel backdrop shared by both pickers.
 *
 * <p>Card size adapts down until the whole grid fits, because the number of kits is admin-driven
 * (10-15 is expected but nothing enforces it) and GUI scale varies a lot between players - a fixed
 * card size would push the bottom row off-screen for someone at scale 3.</p>
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

    /** Chosen grid geometry: how big each card is and how many fit per row. */
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

    /**
     * Picks the largest card size (down to a floor) at which {@code count} cards fit the screen,
     * then centres the resulting grid.
     */
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

        // Even at the smallest size it overflows (a lot of kits on a small window) - use the floor
        // and let the grid start right under the title rather than centring it off-screen.
        int cardWidth = 56;
        int cardHeight = 48;
        int columns = Math.max(1, Math.min(count, (availableWidth + GAP) / (cardWidth + GAP)));
        int rows = (count + columns - 1) / columns;
        int gridWidth = columns * cardWidth + (columns - 1) * GAP;
        return new Grid(cardWidth, cardHeight, columns, rows,
                (screenWidth - gridWidth) / 2, TOP_RESERVED);
    }

    /** Draws the backdrop panel behind a grid. */
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
