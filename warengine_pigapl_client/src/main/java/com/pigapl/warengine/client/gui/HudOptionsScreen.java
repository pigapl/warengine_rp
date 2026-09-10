package com.pigapl.warengine.client.gui;

import com.pigapl.warengine.client.WarClientConfig;
import com.pigapl.warengine.client.hud.CapturePointHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class HudOptionsScreen extends OptionsSubScreen {

    public HudOptionsScreen(Screen lastScreen) {
        super(lastScreen, Minecraft.getInstance().options, Component.literal("War HUD Settings"));
    }

    @Override
    protected void addOptions() {
        if (list == null) {
            return;
        }
        list.addBig(percentSlider("HUD Size", WarClientConfig.HUD_SCALE, 50, 250));
        list.addSmall(
                intSlider("Distance From Top", WarClientConfig.HUD_OFFSET_Y, 0, 200, "px"),
                toggle("Capture Points", WarClientConfig.SHOW_POINTS));
        list.addSmall(
                toggle("Capture Bars", WarClientConfig.SHOW_CAPTURE_BARS),
                toggle("Ticket Bars", WarClientConfig.SHOW_TICKET_BARS));
        list.addSmall(
                toggle("Ticket Numbers", WarClientConfig.SHOW_TICKET_COUNTS),
                toggle("Income Rate", WarClientConfig.SHOW_TICKET_RATE));
        list.addSmall(toggle("Projected Time To Win", WarClientConfig.SHOW_TIME_TO_WIN));
    }

    /**
     * Captions are literal text, not translation keys - {@code Component.translatable} renders a
     * missing key as the raw string, so this needs no lang entry per option.
     */
    private static OptionInstance<Boolean> toggle(String label, ModConfigSpec.BooleanValue value) {
        return OptionInstance.createBoolean(label, value.get(), value::set);
    }

    private static OptionInstance<Integer> intSlider(String label, ModConfigSpec.IntValue value,
                                                     int min, int max, String suffix) {
        return new OptionInstance<>(label, OptionInstance.noTooltip(),
                (caption, v) -> Component.literal(label + ": " + v + suffix),
                new OptionInstance.IntRange(min, max), value.get(), value::set);
    }

    private static OptionInstance<Integer> percentSlider(String label, ModConfigSpec.IntValue value,
                                                         int min, int max) {
        return intSlider(label, value, min, max, "%");
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        // The real HUD, redrawn over this screen's dimming - size is impossible to judge blind, and a
        // mock-up would drift from the thing being sized.
        if (minecraft != null && minecraft.level != null) {
            CapturePointHud.renderOverScreen(graphics);
        }
    }
}
