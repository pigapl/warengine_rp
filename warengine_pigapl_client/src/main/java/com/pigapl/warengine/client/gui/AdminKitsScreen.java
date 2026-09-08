package com.pigapl.warengine.client.gui;

import com.pigapl.warengine.network.AdminKitInfo;
import com.pigapl.warengine.network.ClientboundAdminKitsSnapshotPayload;
import com.pigapl.warengine.network.ServerboundAdminDeleteKitPayload;
import com.pigapl.warengine.network.ServerboundRequestAdminKitsSnapshotPayload;
import com.pigapl.warengine.network.client.ClientAdminCache;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * The kit library: every kit that exists, with icon, display name, limit, and which teams may use
 * it. Replaces having to type {@code /kit name}, {@code /kit limit}, {@code /kit assign|unassign}
 * one command at a time. Editing lives in {@link EditKitScreen}; this screen is the list + Delete.
 */
public final class AdminKitsScreen extends Screen {

    private static final int POLL_INTERVAL_TICKS = 20;
    private static final int ROW_HEIGHT = 22;
    private static final int BUTTON_WIDTH = 50;

    private int pollTicks = 0;
    private int lastSeenRevision = -1;
    private int scroll = 0;

    private int listTop;
    private int listBottom;
    private int listLeft;
    private int listRight;

    public AdminKitsScreen() {
        super(Component.literal("Kit Library"));
    }

    @Override
    protected void init() {
        requestSnapshot();
        lastSeenRevision = ClientAdminCache.revision();

        addRenderableWidget(Button.builder(Component.literal("Back to Event"),
                        b -> Minecraft.getInstance().setScreen(new AdminScreen()))
                .bounds(20, height - 26, 110, 20).build());

        listLeft = 20;
        listRight = width - 20;
        listTop = 40;
        listBottom = height - 34;

        ClientboundAdminKitsSnapshotPayload snap = ClientAdminCache.kitsSnapshot();
        List<AdminKitInfo> kits = snap == null ? List.of() : snap.kits();
        int visibleRows = Math.max(1, (listBottom - listTop) / ROW_HEIGHT);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, kits.size() - visibleRows)));

        int y = listTop;
        for (int i = scroll; i < kits.size() && y < listBottom; i++) {
            AdminKitInfo kit = kits.get(i);
            int rowY = y;
            addRenderableWidget(Button.builder(Component.literal("Edit"),
                            b -> Minecraft.getInstance().setScreen(new EditKitScreen(kit.id())))
                    .bounds(listRight - 2 * (BUTTON_WIDTH + 4), rowY, BUTTON_WIDTH, ROW_HEIGHT - 2)
                    .build());
            addRenderableWidget(Button.builder(Component.literal("Delete").withStyle(ChatFormatting.RED),
                            b -> PacketDistributor.sendToServer(new ServerboundAdminDeleteKitPayload(kit.id())))
                    .bounds(listRight - (BUTTON_WIDTH + 4), rowY, BUTTON_WIDTH, ROW_HEIGHT - 2)
                    .build());
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
        PacketDistributor.sendToServer(new ServerboundRequestAdminKitsSnapshotPayload());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0) {
            scroll = Math.max(0, scroll - (int) Math.signum(scrollY));
            rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 12, PickerLayout.TITLE_COLOR);

        graphics.fill(listLeft, listTop, listRight, listBottom, PickerLayout.PANEL_BG);
        graphics.renderOutline(listLeft, listTop, listRight - listLeft, listBottom - listTop, PickerLayout.PANEL_BORDER);

        ClientboundAdminKitsSnapshotPayload snap = ClientAdminCache.kitsSnapshot();
        if (snap == null) {
            graphics.drawCenteredString(font, Component.literal("Loading...").withStyle(ChatFormatting.GRAY),
                    width / 2, (listTop + listBottom) / 2, PickerLayout.HINT_COLOR);
            return;
        }
        List<AdminKitInfo> kits = snap.kits();
        if (kits.isEmpty()) {
            graphics.drawString(font, "No kits yet - use /kit save <id> in-game.",
                    listLeft + 6, listTop + 6, PickerLayout.HINT_COLOR);
            return;
        }

        int y = listTop;
        for (int i = scroll; i < kits.size() && y < listBottom; i++) {
            AdminKitInfo kit = kits.get(i);
            graphics.pose().pushPose();
            graphics.pose().translate(listLeft + 4, y + 2, 0);
            graphics.pose().scale(1.0f, 1.0f, 1.0f);
            graphics.renderItem(kit.icon(), 0, 0);
            graphics.pose().popPose();

            String teams = kit.assignedTeams().isEmpty() ? "(admin only)" : String.join(", ", kit.assignedTeams());
            String limit = kit.limit() <= 0 ? "unlimited" : ("limit " + kit.limit());
            graphics.drawString(font, kit.displayName() + "  [" + kit.id() + "]  -  " + limit,
                    listLeft + 24, y + 1, 0xFFFFFFFF);
            graphics.drawString(font, "teams: " + teams, listLeft + 24, y + 11, PickerLayout.HINT_COLOR);
            y += ROW_HEIGHT;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
