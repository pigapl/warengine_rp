package com.pigapl.warengine.client.gui;

import com.pigapl.warengine.network.ClientboundAdminScarceSnapshotPayload;
import com.pigapl.warengine.network.ServerboundAdminMarkHeldScarcePayload;
import com.pigapl.warengine.network.ServerboundAdminUnmarkScarceAtPayload;
import com.pigapl.warengine.network.ServerboundRequestAdminScarceSnapshotPayload;
import com.pigapl.warengine.network.client.ClientAdminCache;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

public final class AdminScarceScreen extends Screen {

    private static final int POLL_INTERVAL_TICKS = 20;
    private static final int ROW_HEIGHT = 22;

    private int pollTicks = 0;
    private int lastSeenRevision = -1;

    public AdminScarceScreen() {
        super(Component.literal("Scarce Weapons"));
    }

    @Override
    protected void init() {
        requestSnapshot();
        lastSeenRevision = ClientAdminCache.revision();

        addRenderableWidget(Button.builder(Component.literal("Back to Event"),
                        b -> Minecraft.getInstance().setScreen(new AdminScreen()))
                .bounds(20, height - 26, 110, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Mark item in my hand"),
                        b -> PacketDistributor.sendToServer(new ServerboundAdminMarkHeldScarcePayload()))
                .bounds(width - 20 - 160, height - 26, 160, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Mark several..."),
                        b -> Minecraft.getInstance().setScreen(new AdminScarceBulkScreen(this)))
                .bounds(width - 20 - 160 - 124, height - 26, 120, 20).build());

        ClientboundAdminScarceSnapshotPayload snap = ClientAdminCache.scarceSnapshot();
        List<ItemStack> items = snap == null ? List.of() : snap.items();
        int y = 40;
        for (int i = 0; i < items.size(); i++) {
            int index = i;
            addRenderableWidget(Button.builder(Component.literal("Remove").withStyle(ChatFormatting.RED),
                            b -> PacketDistributor.sendToServer(new ServerboundAdminUnmarkScarceAtPayload(index)))
                    .bounds(width - 20 - 60, y, 60, ROW_HEIGHT - 2).build());
            y += ROW_HEIGHT;
        }
    }

    @Override
    public void tick() {
        if (++pollTicks >= POLL_INTERVAL_TICKS) {
            pollTicks = 0;
            requestSnapshot();
        }
        if (lastSeenRevision != ClientAdminCache.revision()) {
            rebuildWidgets();
        }
    }

    private void requestSnapshot() {
        PacketDistributor.sendToServer(new ServerboundRequestAdminScarceSnapshotPayload());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 12, PickerLayout.TITLE_COLOR);

        int left = 20;
        int right = width - 20 - 70;
        int top = 40;
        int bottom = height - 34;
        graphics.fill(left, top, right + 70, bottom, PickerLayout.PANEL_BG);
        graphics.renderOutline(left, top, right + 70 - left, bottom - top, PickerLayout.PANEL_BORDER);

        ClientboundAdminScarceSnapshotPayload snap = ClientAdminCache.scarceSnapshot();
        if (snap == null) {
            graphics.drawCenteredString(font, Component.literal("Loading...").withStyle(ChatFormatting.GRAY),
                    width / 2, (top + bottom) / 2, PickerLayout.HINT_COLOR);
            return;
        }
        List<ItemStack> items = snap.items();
        if (items.isEmpty()) {
            graphics.drawString(font, "No scarce items marked - hold one and click \"Mark item in my hand\", or use \"Mark several...\".",
                    left + 6, top + 6, PickerLayout.HINT_COLOR);
            return;
        }

        int y = top;
        for (ItemStack item : items) {
            graphics.pose().pushPose();
            graphics.pose().translate(left + 4, y + 2, 0);
            graphics.renderItem(item, 0, 0);
            graphics.pose().popPose();
            graphics.drawString(font, item.getHoverName().getString(), left + 24, y + 6, 0xFFFFFFFF);
            y += ROW_HEIGHT;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
