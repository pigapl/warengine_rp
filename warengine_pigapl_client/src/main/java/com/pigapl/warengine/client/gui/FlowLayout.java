package com.pigapl.warengine.client.gui;

/**
 * Places a left-to-right run of fixed-width items, wrapping to a new row instead of overflowing off
 * the right edge. Replaces the raw pixel math ({@code x=20}, {@code width/2-84}, ...) the admin
 * screens' button rows used, which assumed a wide-enough window and let buttons overlap or run
 * off-screen at small GUI scales.
 *
 * <p>Create one per row group, call {@link #next(int)} per item width for its {@code x}/{@code y},
 * then read {@link #bottom()} for how much vertical space the (possibly wrapped) group used.</p>
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

    /** X/Y for the next item of this width; advances the cursor, wrapping to a new row if it wouldn't fit. */
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

    /** Forces the next item onto a fresh row, even if the current one still has room. */
    public void forceNewRow() {
        if (rowHasItem) {
            x = startX;
            y += rowHeight + gap;
            rowHasItem = false;
        }
    }

    /** Bottom Y of the last row placed - use to reserve space below this group. */
    public int bottom() {
        return y + rowHeight;
    }
}
