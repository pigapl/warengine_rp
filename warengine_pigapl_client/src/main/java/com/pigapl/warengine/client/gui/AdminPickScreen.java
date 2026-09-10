package com.pigapl.warengine.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

public final class AdminPickScreen extends Screen {

    public record Entry(String id, String label) {}

    private static final int ROW_HEIGHT = 24;
    private static final int ROW_WIDTH = 160;

    private final List<Entry> entries;
    private final Consumer<String> onPick;
    private final Screen parent;

    public AdminPickScreen(String title, List<Entry> entries, Consumer<String> onPick, Screen parent) {
        super(Component.literal(title));
        this.entries = entries;
        this.onPick = onPick;
        this.parent = parent;
    }

    @Override
    protected void init() {
        int maxRows = Math.max(1, (height - 90) / ROW_HEIGHT);
        int columns = Math.max(1, (int) Math.ceil(entries.size() / (double) maxRows));
        int totalWidth = columns * (ROW_WIDTH + 10) - 10;
        int startX = (width - totalWidth) / 2;

        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            int col = i / maxRows;
            int row = i % maxRows;
            int x = startX + col * (ROW_WIDTH + 10);
            int y = 40 + row * ROW_HEIGHT;
            addRenderableWidget(Button.builder(Component.literal(e.label()), b -> {
                        onPick.accept(e.id());
                        Minecraft.getInstance().setScreen(parent);
                    })
                    .bounds(x, y, ROW_WIDTH, 20).build());
        }

        addRenderableWidget(Button.builder(Component.literal("Cancel"),
                        b -> Minecraft.getInstance().setScreen(parent))
                .bounds(width / 2 - 60, height - 26, 120, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 20, PickerLayout.TITLE_COLOR);
        if (entries.isEmpty()) {
            graphics.drawCenteredString(font,
                    Component.literal("(nothing to pick)").withStyle(net.minecraft.ChatFormatting.GRAY),
                    width / 2, 40, PickerLayout.HINT_COLOR);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
