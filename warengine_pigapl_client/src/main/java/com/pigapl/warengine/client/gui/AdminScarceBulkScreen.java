package com.pigapl.warengine.client.gui;

import com.pigapl.warengine.kit.ScarceItems;
import com.pigapl.warengine.network.ClientboundAdminScarceSnapshotPayload;
import com.pigapl.warengine.network.ServerboundAdminBulkScarcePayload;
import com.pigapl.warengine.network.client.ClientAdminCache;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Tick several items scarce at once, instead of hold-one-and-click per item.
 *
 * <p>Deliberately does NOT poll the server: a snapshot is taken when it opens and held until Apply, so
 * a reply landing mid-edit can never wipe what the admin has ticked. Two tabs on purpose - the
 * inventory is the fast path while gathering a loadout, the kit list is the audit that catches an item
 * sitting in a kit you forgot about.</p>
 */
public final class AdminScarceBulkScreen extends Screen {

    private static final int CELL = 20;
    private static final int CELL_GAP = 2;
    private static final int TICKED_BG = 0xA02E7D3E;
    private static final int PLAIN_BG = 0x50202028;
    private static final int TICKED_BORDER = 0xFF5AC46A;

    private static final int TAB_INVENTORY = 0;
    private static final int TAB_KITS = 1;

    private final Screen parent;

    private int tab = TAB_INVENTORY;
    private int scrollRows = 0;

    /** Captured once on open - see the class note about not polling. */
    private List<ItemStack> scarceNow;
    private List<ItemStack> inventoryItems;
    private List<ItemStack> kitItems;
    private boolean[] inventoryTicked;
    private boolean[] kitTicked;

    private int left;
    private int right;
    private int top;
    private int bottom;

    public AdminScarceBulkScreen(Screen parent) {
        super(Component.literal("Mark Scarce Items"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (scarceNow == null) {
            capture();
        }

        left = 20;
        right = width - 20;
        top = 52;
        bottom = height - 40;

        addRenderableWidget(Button.builder(tabLabel("My inventory", TAB_INVENTORY), b -> switchTab(TAB_INVENTORY))
                .bounds(left, 26, 110, 20).build());
        addRenderableWidget(Button.builder(tabLabel("All kit items", TAB_KITS), b -> switchTab(TAB_KITS))
                .bounds(left + 114, 26, 110, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
                .bounds(left, height - 32, 80, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Apply"), b -> apply())
                .bounds(left + 84, height - 32, 80, 20).build());
    }

    private Component tabLabel(String name, int which) {
        return tab == which ? Component.literal("> " + name).withStyle(ChatFormatting.YELLOW)
                : Component.literal(name);
    }

    private void switchTab(int which) {
        tab = which;
        scrollRows = 0;
        rebuildWidgets();
    }

    private void capture() {
        ClientboundAdminScarceSnapshotPayload snap = ClientAdminCache.scarceSnapshot();
        scarceNow = snap == null ? List.of() : List.copyOf(snap.items());
        kitItems = snap == null ? List.of() : List.copyOf(snap.kitItems());

        inventoryItems = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            Inventory inv = mc.player.getInventory();
            addDistinct(inventoryItems, inv.items);
            addDistinct(inventoryItems, inv.armor);
            addDistinct(inventoryItems, inv.offhand);
        }

        inventoryTicked = tickedFrom(inventoryItems);
        kitTicked = tickedFrom(kitItems);
    }

    private void addDistinct(List<ItemStack> out, List<ItemStack> candidates) {
        for (ItemStack stack : candidates) {
            if (!stack.isEmpty() && !ScarceItems.matchesAny(out, stack)) {
                out.add(stack.copy());
            }
        }
    }

    private boolean[] tickedFrom(List<ItemStack> items) {
        boolean[] ticked = new boolean[items.size()];
        for (int i = 0; i < items.size(); i++) {
            ticked[i] = ScarceItems.matchesAny(scarceNow, items.get(i));
        }
        return ticked;
    }

    private List<ItemStack> items() {
        return tab == TAB_INVENTORY ? inventoryItems : kitItems;
    }

    private boolean[] ticked() {
        return tab == TAB_INVENTORY ? inventoryTicked : kitTicked;
    }

    /**
     * Both tabs are sent, not just the visible one - the same weapon can appear in each, and only the
     * union says what the admin actually decided.
     */
    private void apply() {
        List<ItemStack> toMark = new ArrayList<>();
        List<ItemStack> toClear = new ArrayList<>();
        collectChanges(inventoryItems, inventoryTicked, toMark, toClear);
        collectChanges(kitItems, kitTicked, toMark, toClear);

        if (!toMark.isEmpty() || !toClear.isEmpty()) {
            PacketDistributor.sendToServer(new ServerboundAdminBulkScarcePayload(toMark, toClear));
        }
        onClose();
    }

    private void collectChanges(List<ItemStack> items, boolean[] ticked,
                                List<ItemStack> toMark, List<ItemStack> toClear) {
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            boolean wasScarce = ScarceItems.matchesAny(scarceNow, stack);
            if (ticked[i] && !wasScarce && !ScarceItems.matchesAny(toMark, stack)) {
                toMark.add(stack.copy());
            } else if (!ticked[i] && wasScarce && !ScarceItems.matchesAny(toClear, stack)) {
                toClear.add(stack.copy());
            }
        }
    }

    private int pendingChanges() {
        List<ItemStack> toMark = new ArrayList<>();
        List<ItemStack> toClear = new ArrayList<>();
        collectChanges(inventoryItems, inventoryTicked, toMark, toClear);
        collectChanges(kitItems, kitTicked, toMark, toClear);
        return toMark.size() + toClear.size();
    }

    private int columns() {
        return Math.max(1, (right - left - 8 + CELL_GAP) / (CELL + CELL_GAP));
    }

    private int visibleRows() {
        return Math.max(1, (bottom - top - 8 + CELL_GAP) / (CELL + CELL_GAP));
    }

    private int cellX(int indexOnScreen) {
        return left + 4 + (indexOnScreen % columns()) * (CELL + CELL_GAP);
    }

    private int cellY(int indexOnScreen) {
        return top + 4 + (indexOnScreen / columns()) * (CELL + CELL_GAP);
    }

    /** Index into {@link #items()} under the cursor, or -1. */
    private int cellAt(double mouseX, double mouseY) {
        int first = scrollRows * columns();
        int shown = Math.min(items().size() - first, visibleRows() * columns());
        for (int i = 0; i < shown; i++) {
            int x = cellX(i);
            int y = cellY(i);
            if (mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + CELL) {
                return first + i;
            }
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int index = cellAt(mouseX, mouseY);
        if (index >= 0) {
            ticked()[index] = !ticked()[index];
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0 && mouseY >= top && mouseY < bottom) {
            int maxRow = Math.max(0, (items().size() + columns() - 1) / columns() - visibleRows());
            scrollRows = Math.max(0, Math.min(maxRow, scrollRows - (int) Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 12, PickerLayout.TITLE_COLOR);

        graphics.fill(left, top, right, bottom, PickerLayout.PANEL_BG);
        graphics.renderOutline(left, top, right - left, bottom - top, PickerLayout.PANEL_BORDER);

        List<ItemStack> items = items();
        if (items.isEmpty()) {
            graphics.drawString(font, tab == TAB_INVENTORY
                            ? "Your inventory is empty - grab a loadout, then reopen this."
                            : "No kits saved yet, so there is nothing to list.",
                    left + 6, top + 8, PickerLayout.HINT_COLOR);
        } else {
            drawGrid(graphics, items);
        }

        int changes = pendingChanges();
        graphics.drawString(font, changes == 0 ? "Click items to tick them scarce. No changes yet."
                        : changes + " change(s) pending - Apply to save them",
                left + 170, height - 26,
                changes == 0 ? PickerLayout.HINT_COLOR : 0xFF5AC46A);

        int hovered = cellAt(mouseX, mouseY);
        if (hovered >= 0) {
            ItemStack stack = items.get(hovered);
            List<Component> lines = new ArrayList<>();
            lines.add(stack.getHoverName());
            lines.add(ticked()[hovered]
                    ? Component.literal("scarce - only issued at round start").withStyle(ChatFormatting.GREEN)
                    : Component.literal("normal - handed out with the kit").withStyle(ChatFormatting.GRAY));
            graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
    }

    private void drawGrid(GuiGraphics graphics, List<ItemStack> items) {
        graphics.enableScissor(left, top, right, bottom);
        int first = scrollRows * columns();
        int shown = Math.min(items.size() - first, visibleRows() * columns());
        for (int i = 0; i < shown; i++) {
            int index = first + i;
            int x = cellX(i);
            int y = cellY(i);
            boolean on = ticked()[index];
            graphics.fill(x, y, x + CELL, y + CELL, on ? TICKED_BG : PLAIN_BG);
            if (on) {
                graphics.renderOutline(x, y, CELL, CELL, TICKED_BORDER);
            }
            graphics.renderItem(items.get(index), x + 2, y + 2);
        }
        graphics.disableScissor();
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
