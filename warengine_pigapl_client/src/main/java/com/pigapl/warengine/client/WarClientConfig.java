package com.pigapl.warengine.client;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Per-player HUD preferences. CLIENT type, so it never travels to the server - none of this affects
 * gameplay. {@code set()} persists to the toml at runtime, so there is no separate save step.
 */
public final class WarClientConfig {

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue HUD_SCALE = B
            .comment("Size of the capture-point HUD, as a percentage. 100 = the same size as vanilla text.")
            .defineInRange("hud.scalePercent", 100, 50, 250);

    public static final ModConfigSpec.IntValue HUD_OFFSET_Y = B
            .comment("Pixels down from the top of the screen to draw the HUD.")
            .defineInRange("hud.offsetY", 4, 0, 200);

    public static final ModConfigSpec.BooleanValue SHOW_POINTS = B
            .comment("Show the row of capture-point pills.")
            .define("hud.showPoints", true);

    public static final ModConfigSpec.BooleanValue SHOW_CAPTURE_BARS = B
            .comment("Show the capture-progress bar under each point.")
            .define("hud.showCaptureBars", true);

    public static final ModConfigSpec.BooleanValue SHOW_TICKET_BARS = B
            .comment("Show each team's ticket progress as a bar either side of the points -",
                     "yours on the left, everyone else on the right.")
            .define("hud.showTicketBars", true);

    public static final ModConfigSpec.BooleanValue SHOW_TICKET_COUNTS = B
            .comment("Show the small number at the end of each ticket bar.")
            .define("hud.showTicketCounts", true);

    public static final ModConfigSpec.BooleanValue SHOW_TICKET_RATE = B
            .comment("Show each team's current income (+3/s). Off by default - it is the most",
                     "cluttering piece and the bars already show who is pulling ahead.")
            .define("hud.showTicketRate", false);

    public static final ModConfigSpec.BooleanValue SHOW_TIME_TO_WIN = B
            .comment("Show a projected time-to-win per team at their current income. Off by default.")
            .define("hud.showTimeToWin", false);

    public static final ModConfigSpec SPEC = B.build();

    private WarClientConfig() {}

    public static float scale() {
        return HUD_SCALE.get() / 100.0f;
    }
}
