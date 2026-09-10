package com.pigapl.warengine.client.gui;

/**
 * Places a left-to-right run of fixed-width items, wrapping instead of overflowing the right edge.
 * Replaces raw pixel math, which let buttons overlap or run off-screen at small GUI scales.
 */
public final class FlowLayout {

    private final int startX;
    private final int maxRight;
    private final int rowHeight;
    private final int gap;

    private int x;
    private int y;
    private boolean rowHasItem = false;

    public FlowLayout(int startX, int startY, int maxRight, int rowHeight, int gap) {
        this.startX = startX;
        this.maxRight = maxRight;
        this.rowHeight = rowHeight;
        this.gap = gap;
        this.x = startX;
        this.y = startY;
    }

    public int[] next(int width) {
        if (rowHasItem && x + width > maxRight) {
            x = startX;
            y += rowHeight + gap;
            rowHasItem = false;
        }
        int px = x;
        int py = y;
        x += width + gap;
        rowHasItem = true;
        return new int[]{px, py};
    }

    public void forceNewRow() {
        if (rowHasItem) {
            x = startX;
            y += rowHeight + gap;
            rowHasItem = false;
        }
    }

    public int bottom() {
        return y + rowHeight;
    }
}
