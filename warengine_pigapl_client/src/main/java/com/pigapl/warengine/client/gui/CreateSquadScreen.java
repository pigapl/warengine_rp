package com.pigapl.warengine.client.gui;

import com.pigapl.warengine.network.KitBudgetEntry;
import com.pigapl.warengine.network.ServerboundCreateSquadPayload;
import com.pigapl.warengine.network.ServerboundRequestKitBudgetPayload;
import com.pigapl.warengine.network.client.ClientKitBudgetCache;
import com.pigapl.warengine.squad.SquadService;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The "create" half of the squad step - name, roster limit, and the per-kit reservation steppers.
 * A stepper is capped at that kit's {@code remaining}; the server re-validates on submit regardless.
 */
public final class CreateSquadScreen extends Screen {

    private static final int FIELD_WIDTH = 200;
    private static final int FIELD_HEIGHT = 20;
    private static final int LABEL_H = 11;
    private static final int NAME_Y = LABEL_H;
    private static final int LIMIT_LABEL_Y = NAME_Y + FIELD_HEIGHT + 5;
    private static final int LIMIT_Y = LIMIT_LABEL_Y + LABEL_H;
    private static final int FIELDS_H = LIMIT_Y + FIELD_HEIGHT + 10;
    private static final int DEFAULT_LIMIT = 6;
    private static final int PANEL_WIDTH = 260;
    private static final int ROW_H = 22;
    private static final int MAX_VISIBLE_ROWS = 6;
    private static final int POLL_INTERVAL_TICKS = 20;

    private final Map<String, Integer> chosen = new LinkedHashMap<>();

    private EditBox nameBox;
    private EditBox limitBox;
    private Component error = null;

    private int scroll = 0;
    private int pollTicks = 0;
    private int lastBudgetRevision = -2;

    private int panelTop;
    private int listTop;
    private int visibleRows;
    private int buttonsY;

    public CreateSquadScreen() {
        super(Component.literal("Create Squad"));
    }

    @Override
    protected void init() {
        if (lastBudgetRevision == -2) {
            PacketDistributor.sendToServer(new ServerboundRequestKitBudgetPayload());
        }
        lastBudgetRevision = ClientKitBudgetCache.revision();

        List<KitBudgetEntry> budgets = ClientKitBudgetCache.entries();
        int centerX = width / 2;

        int rowsShown = Math.min(budgets.size(), MAX_VISIBLE_ROWS);
        int listBlock = budgets.isEmpty() ? 0 : (14 + rowsShown * ROW_H);
        int contentH = FIELDS_H + listBlock + 34;
        int top = Math.max(28, height / 2 - contentH / 2);
        panelTop = top;
        visibleRows = rowsShown;

        String prevName = nameBox != null ? nameBox.getValue() : "";
        String prevLimit = limitBox != null ? limitBox.getValue() : Integer.toString(DEFAULT_LIMIT);

        nameBox = new EditBox(font, centerX - FIELD_WIDTH / 2, top + NAME_Y, FIELD_WIDTH, FIELD_HEIGHT,
                Component.literal("Squad name"));
        nameBox.setMaxLength(SquadService.NAME_MAX_LENGTH);
        nameBox.setHint(Component.literal("e.g. Alpha").withStyle(ChatFormatting.DARK_GRAY));
        nameBox.setValue(prevName);
        addRenderableWidget(nameBox);
        setInitialFocus(nameBox);

        limitBox = new EditBox(font, centerX - FIELD_WIDTH / 2, top + LIMIT_Y, FIELD_WIDTH, FIELD_HEIGHT,
                Component.literal("Max players"));
        limitBox.setMaxLength(3);
        limitBox.setFilter(s -> s.isEmpty() || s.chars().allMatch(Character::isDigit));
        limitBox.setValue(prevLimit);
        addRenderableWidget(limitBox);

        // Drop entries for kits that lost their budget, clamp any that now exceed what's left.
        chosen.keySet().removeIf(k -> budgets.stream().noneMatch(e -> e.kitId().equals(k)));
        for (KitBudgetEntry e : budgets) {
            Integer c = chosen.get(e.kitId());
            if (c != null && c > e.remaining()) {
                if (e.remaining() > 0) {
                    chosen.put(e.kitId(), e.remaining());
                } else {
                    chosen.remove(e.kitId());
                }
            }
        }

        listTop = top + FIELDS_H + 14;
        if (!budgets.isEmpty()) {
            scroll = Math.max(0, Math.min(scroll, budgets.size() - rowsShown));
            int stepX = centerX + PANEL_WIDTH / 2 - 96;
            for (int r = 0; r < rowsShown; r++) {
                KitBudgetEntry entry = budgets.get(scroll + r);
                int rowY = listTop + r * ROW_H;
                int cur = chosen.getOrDefault(entry.kitId(), 0);

                Button minus = Button.builder(Component.literal("-"),
                                b -> adjust(entry.kitId(), -1, entry.remaining()))
                        .bounds(stepX, rowY, 16, 18).build();
                minus.active = cur > 0;
                addRenderableWidget(minus);

                Button plus = Button.builder(Component.literal("+"),
                                b -> adjust(entry.kitId(), 1, entry.remaining()))
                        .bounds(stepX + 52, rowY, 16, 18).build();
                plus.active = cur < entry.remaining();
                addRenderableWidget(plus);
            }
        }

        buttonsY = budgets.isEmpty() ? top + FIELDS_H + 4 : listTop + rowsShown * ROW_H + 8;
        int buttonWidth = 90;
        // Confirm on the right - players on stream kept hitting the wrong one when it was on the left.
        addRenderableWidget(Button.builder(Component.literal("Back"),
                        b -> Minecraft.getInstance().setScreen(new SquadPickerScreen()))
                .bounds(centerX - buttonWidth - 4, buttonsY, buttonWidth, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Create"), b -> tryCreate())
                .bounds(centerX + 4, buttonsY, buttonWidth, 20)
                .build());
    }

    @Override
    public void tick() {
        if (++pollTicks >= POLL_INTERVAL_TICKS) {
            pollTicks = 0;
            PacketDistributor.sendToServer(new ServerboundRequestKitBudgetPayload());
        }
        if (lastBudgetRevision != ClientKitBudgetCache.revision()) {
            rebuildPreservingFocus();
        }
    }

    /** {@code init()} focuses the name box, so the ~1/s poll rebuild would steal focus mid-type. */
    private void rebuildPreservingFocus() {
        boolean nameFocused = nameBox != null && nameBox.isFocused();
        boolean limitFocused = limitBox != null && limitBox.isFocused();
        rebuildWidgets();
        if (nameFocused) {
            setFocused(nameBox);
        } else if (limitFocused) {
            setFocused(limitBox);
        }
    }

    private void adjust(String kitId, int delta, int remaining) {
        int next = Math.max(0, Math.min(remaining, chosen.getOrDefault(kitId, 0) + delta));
        if (next == 0) {
            chosen.remove(kitId);
        } else {
            chosen.put(kitId, next);
        }
        rebuildPreservingFocus();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int rows = ClientKitBudgetCache.entries().size();
        if (scrollY != 0 && rows > MAX_VISIBLE_ROWS) {
            scroll = Math.max(0, Math.min(rows - MAX_VISIBLE_ROWS, scroll - (int) Math.signum(scrollY)));
            rebuildPreservingFocus();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void tryCreate() {
        String name = nameBox.getValue().trim();
        if (name.isEmpty()) {
            error = Component.literal("Name can't be blank.").withStyle(ChatFormatting.RED);
            return;
        }
        int limit;
        try {
            limit = Integer.parseInt(limitBox.getValue().trim());
        } catch (NumberFormatException e) {
            error = Component.literal("Enter a max player count.").withStyle(ChatFormatting.RED);
            return;
        }
        if (limit < SquadService.MIN_LIMIT || limit > SquadService.MAX_LIMIT) {
            error = Component.literal("Max players must be " + SquadService.MIN_LIMIT
                    + "-" + SquadService.MAX_LIMIT + ".").withStyle(ChatFormatting.RED);
            return;
        }
        PacketDistributor.sendToServer(new ServerboundCreateSquadPayload(name, limit, new LinkedHashMap<>(chosen)));
        onClose();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        int pad = 18;
        int left = width / 2 - PANEL_WIDTH / 2 - pad;
        int right = width / 2 + PANEL_WIDTH / 2 + pad;
        int top = panelTop - pad - 14;
        int bottom = buttonsY + 20 + pad;
        graphics.fill(left, top, right, bottom, PickerLayout.PANEL_BG);
        graphics.renderOutline(left, top, right - left, bottom - top, PickerLayout.PANEL_BORDER);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, panelTop - 18, PickerLayout.TITLE_COLOR);

        int centerX = width / 2;
        int fieldX = centerX - FIELD_WIDTH / 2;
        graphics.drawString(font, "Squad name", fieldX, panelTop, PickerLayout.HINT_COLOR);
        graphics.drawString(font, "Max players (" + SquadService.MIN_LIMIT + "-" + SquadService.MAX_LIMIT + ")",
                fieldX, panelTop + LIMIT_LABEL_Y, PickerLayout.HINT_COLOR);

        List<KitBudgetEntry> budgets = ClientKitBudgetCache.entries();

        if (!budgets.isEmpty()) {
            graphics.drawString(font, "Squad kit reservations", centerX - PANEL_WIDTH / 2,
                    listTop - 12, 0xFFFFFFFF);
            int stepX = centerX + PANEL_WIDTH / 2 - 96;
            for (int r = 0; r < visibleRows; r++) {
                KitBudgetEntry entry = budgets.get(scroll + r);
                int rowY = listTop + r * ROW_H;
                int cur = chosen.getOrDefault(entry.kitId(), 0);

                graphics.renderItem(entry.icon(), centerX - PANEL_WIDTH / 2, rowY - 1);
                graphics.drawString(font, entry.displayName(), centerX - PANEL_WIDTH / 2 + 22, rowY + 4, 0xFFFFFFFF);
                graphics.drawCenteredString(font, Integer.toString(cur), stepX + 34, rowY + 4, 0xFFFFFFFF);
                graphics.drawString(font, "/ " + entry.remaining(), stepX + 72, rowY + 4, PickerLayout.HINT_COLOR);
            }
            if (budgets.size() > MAX_VISIBLE_ROWS) {
                graphics.drawString(font, "scroll for more (" + budgets.size() + " kits)",
                        centerX - PANEL_WIDTH / 2, listTop + visibleRows * ROW_H - 4, PickerLayout.HINT_COLOR);
            }
        }

        Component footer = error != null ? error
                : Component.literal("Founding a squad joins it immediately.").withStyle(ChatFormatting.GRAY);
        graphics.drawCenteredString(font, footer, centerX, buttonsY + 26, PickerLayout.HINT_COLOR);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
