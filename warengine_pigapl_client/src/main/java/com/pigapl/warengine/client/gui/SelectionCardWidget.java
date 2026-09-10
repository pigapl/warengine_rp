package com.pigapl.warengine.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * One card in a picker grid, shared by both pickers. Drawn entirely from fills and text - no texture
 * assets - so the palette lives here rather than in a resource pack.
 */
public final class SelectionCardWidget extends AbstractButton {

    private static final int BG = 0xFF1E1E24;
    private static final int BG_HOVER = 0xFF2C2C36;
    private static final int BG_DISABLED = 0xFF16161A;
    private static final int BORDER = 0xFF3A3A46;
    private static final int BORDER_HOVER = 0xFF7A7A8C;
    private static final int TITLE = 0xFFFFFFFF;
    private static final int TITLE_DISABLED = 0xFF6A6A72;
    private static final int SUBTITLE = 0xFFB0B0BC;

    private final ItemStack icon;
    private final Component subtitle;
    private final int accent;
    private final boolean selected;
    private final Runnable onSelect;

    public SelectionCardWidget(int x, int y, int width, int height, Component title, Component subtitle,
                               ItemStack icon, int accent, boolean selected, Runnable onSelect) {
        super(x, y, width, height, title);
        this.icon = icon;
        this.subtitle = subtitle;
        this.accent = accent;
        this.selected = selected;
        this.onSelect = onSelect;
    }

    @Override
    public void onPress() {
        onSelect.run();
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var font = Minecraft.getInstance().font;
        boolean hovered = isHoveredOrFocused() && active;

        int background = !active ? BG_DISABLED : hovered ? BG_HOVER : BG;
        int border = !active ? BORDER : selected ? accent : hovered ? BORDER_HOVER : BORDER;

        graphics.fill(getX(), getY(), getX() + width, getY() + height, background);
        graphics.renderOutline(getX(), getY(), width, height, border);
        if (selected) {
            graphics.renderOutline(getX() + 1, getY() + 1, width - 2, height - 2, accent);
        }

        int centerX = getX() + width / 2;

        if (!icon.isEmpty()) {
            graphics.pose().pushPose();
            graphics.pose().translate(centerX - 16, getY() + 10, 0);
            graphics.pose().scale(2.0f, 2.0f, 1.0f);
            graphics.renderItem(icon, 0, 0);
            graphics.pose().popPose();
        }

        int textTop = icon.isEmpty() ? getY() + height / 2 - 10 : getY() + 48;
        String name = font.plainSubstrByWidth(getMessage().getString(), width - 8);
        graphics.drawCenteredString(font, name, centerX, textTop, active ? TITLE : TITLE_DISABLED);

        if (subtitle != null) {
            graphics.drawCenteredString(font, subtitle, centerX, textTop + 12,
                    !active ? TITLE_DISABLED : selected ? accent : SUBTITLE);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
