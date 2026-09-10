package com.pigapl.warengine.client.gui;

import com.pigapl.warengine.network.AdminBudgetRow;
import com.pigapl.warengine.network.ClientboundAdminBudgetSnapshotPayload;
import com.pigapl.warengine.network.ServerboundAdminSetBudgetPayload;
import com.pigapl.warengine.network.ServerboundRequestAdminBudgetSnapshotPayload;
import com.pigapl.warengine.network.client.ClientAdminCache;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Locale;

public final class AdminBudgetScreen extends Screen {

    private static final int POLL_INTERVAL_TICKS = 20;
    private static final int ROW_HEIGHT = 22;
    private static final int HEADER_HEIGHT = 14;
    private static final int STEP_W = 16;

    private int pollTicks = 0;
    private int lastSeenRevision = -1;
    private int scroll = 0;

    private int listTop;
    private int listBottom;
    private int listLeft;
    private int listRight;

    public AdminBudgetScreen() {
        super(Component.literal("Kit Budgets"));
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

        List<AdminBudgetRow> rows = rows();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rows.size() - 1)));
        int clearX = listRight - 44;
        int plusX = clearX - 6 - STEP_W;
        int minusX = plusX - 4 - STEP_W;

        String lastTeam = null;
        int y = listTop;
        for (int i = scroll; i < rows.size() && y < listBottom; i++) {
            AdminBudgetRow row = rows.get(i);
            if (!row.team().equals(lastTeam)) {
                lastTeam = row.team();
                y += HEADER_HEIGHT;
                if (y >= listBottom) {
                    break;
                }
            }
            int rowY = y;
            addRenderableWidget(Button.builder(Component.literal("-"),
                            b -> setBudget(row, Math.max(0, row.total() - 1)))
                    .bounds(minusX, rowY, STEP_W, 18).build());
            addRenderableWidget(Button.builder(Component.literal("+"),
                            b -> setBudget(row, row.total() + 1))
                    .bounds(plusX, rowY, STEP_W, 18).build());
            Button clear = Button.builder(Component.literal("Clear"), b -> setBudget(row, 0))
                    .bounds(clearX, rowY, 40, 18).build();
            clear.active = row.total() > 0;
            addRenderableWidget(clear);
            y += ROW_HEIGHT;
        }
    }

    private List<AdminBudgetRow> rows() {
        ClientboundAdminBudgetSnapshotPayload snap = ClientAdminCache.budgetSnapshot();
        return snap == null ? List.of() : snap.rows();
    }

    private void setBudget(AdminBudgetRow row, int count) {
        PacketDistributor.sendToServer(new ServerboundAdminSetBudgetPayload(row.team(), row.kitId(), count));
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
        PacketDistributor.sendToServer(new ServerboundRequestAdminBudgetSnapshotPayload());
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

        ClientboundAdminBudgetSnapshotPayload snap = ClientAdminCache.budgetSnapshot();
        if (snap == null) {
            graphics.drawCenteredString(font, Component.literal("Loading...").withStyle(ChatFormatting.GRAY),
                    width / 2, (listTop + listBottom) / 2, PickerLayout.HINT_COLOR);
            return;
        }
        List<AdminBudgetRow> rows = snap.rows();
        if (rows.isEmpty()) {
            graphics.drawString(font, "No teams with usable kits yet - set up teams and /kit assign first.",
                    listLeft + 6, listTop + 6, PickerLayout.HINT_COLOR);
            return;
        }

        int clearX = listRight - 44;
        int plusX = clearX - 6 - STEP_W;
        int minusX = plusX - 4 - STEP_W;

        String lastTeam = null;
        int y = listTop;
        for (int i = scroll; i < rows.size() && y < listBottom; i++) {
            AdminBudgetRow row = rows.get(i);
            if (!row.team().equals(lastTeam)) {
                lastTeam = row.team();
                graphics.drawString(font, row.team().toUpperCase(Locale.ROOT), listLeft + 4, y + 3, 0xFFB9B9C6);
                y += HEADER_HEIGHT;
                if (y >= listBottom) {
                    break;
                }
            }
            graphics.renderItem(row.icon(), listLeft + 6, y);
            graphics.drawString(font, row.displayName() + "  [" + row.kitId() + "]", listLeft + 28, y + 1, 0xFFFFFFFF);
            String status = row.total() <= 0
                    ? "no budget - per-squad limit applies"
                    : row.reserved() + " reserved / " + row.total() + " budget";
            graphics.drawString(font, status, listLeft + 28, y + 11,
                    row.total() > 0 ? 0xFF9AD19A : PickerLayout.HINT_COLOR);
            graphics.drawCenteredString(font, Integer.toString(Math.max(0, row.total())),
                    minusX + STEP_W + (plusX - minusX - STEP_W) / 2, y + 5, 0xFFFFFFFF);
            y += ROW_HEIGHT;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
