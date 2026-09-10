package com.pigapl.warengine.client.gui;

import com.pigapl.warengine.network.AdminKitInfo;
import com.pigapl.warengine.network.ClientboundAdminKitsSnapshotPayload;
import com.pigapl.warengine.network.ServerboundAdminToggleKitTeamPayload;
import com.pigapl.warengine.network.ServerboundAdminUpdateKitPayload;
import com.pigapl.warengine.network.ServerboundRequestAdminKitsSnapshotPayload;
import com.pigapl.warengine.network.client.ClientAdminCache;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

public final class EditKitScreen extends Screen {

    private static final int FIELD_WIDTH = 180;
    private static final int POLL_INTERVAL_TICKS = 20;

    private final String kitId;
    private EditBox nameBox;
    private EditBox limitBox;
    private int pollTicks = 0;
    private int lastSeenRevision = -1;
    private Component status = null;

    public EditKitScreen(String kitId) {
        super(Component.literal("Edit Kit"));
        this.kitId = kitId;
    }

    private AdminKitInfo find() {
        ClientboundAdminKitsSnapshotPayload snap = ClientAdminCache.kitsSnapshot();
        if (snap == null) {
            return null;
        }
        for (AdminKitInfo k : snap.kits()) {
            if (k.id().equals(kitId)) {
                return k;
            }
        }
        return null;
    }

    @Override
    protected void init() {
        requestSnapshot();
        lastSeenRevision = ClientAdminCache.revision();

        AdminKitInfo kit = find();
        int centerX = width / 2;
        int top = 40;

        nameBox = new EditBox(font, centerX - FIELD_WIDTH / 2, top, FIELD_WIDTH, 20, Component.literal("Display name"));
        nameBox.setMaxLength(48);
        nameBox.setValue(kit == null ? "" : kit.displayName());
        addRenderableWidget(nameBox);
        setInitialFocus(nameBox);

        limitBox = new EditBox(font, centerX - FIELD_WIDTH / 2, top + 24, FIELD_WIDTH, 20, Component.literal("Limit"));
        limitBox.setMaxLength(4);
        limitBox.setFilter(s -> s.isEmpty() || s.chars().allMatch(Character::isDigit));
        limitBox.setValue(kit == null ? "0" : Integer.toString(kit.limit()));
        addRenderableWidget(limitBox);

        int buttonWidth = 90;
        addRenderableWidget(Button.builder(Component.literal("Save"), b -> save())
                .bounds(centerX - buttonWidth - 4, top + 50, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"),
                        b -> Minecraft.getInstance().setScreen(new AdminKitsScreen()))
                .bounds(centerX + 4, top + 50, buttonWidth, 20).build());

        ClientboundAdminKitsSnapshotPayload snap = ClientAdminCache.kitsSnapshot();
        List<String> teamIds = snap == null ? List.of() : snap.teamIds();
        List<String> assigned = kit == null ? List.of() : kit.assignedTeams();
        int ty = top + 90;
        int tx = centerX - FIELD_WIDTH / 2;
        int col = 0;
        for (String team : teamIds) {
            boolean has = assigned.contains(team);
            int bw = 60;
            addRenderableWidget(Button.builder(
                            Component.literal((has ? "✓ " : "") + team).withStyle(has ? ChatFormatting.GREEN : ChatFormatting.GRAY),
                            b -> PacketDistributor.sendToServer(new ServerboundAdminToggleKitTeamPayload(kitId, team)))
                    .bounds(tx + col * (bw + 4), ty, bw, 20).build());
            col++;
            if (tx + (col + 1) * (bw + 4) > width - 20) {
                col = 0;
                ty += 24;
            }
        }
    }

    private void save() {
        int limit;
        try {
            limit = Integer.parseInt(limitBox.getValue().trim());
        } catch (NumberFormatException e) {
            limit = 0;
        }
        PacketDistributor.sendToServer(new ServerboundAdminUpdateKitPayload(kitId, nameBox.getValue().trim(), limit));
        status = Component.literal("Saved.").withStyle(ChatFormatting.GREEN);
    }

    @Override
    public void tick() {
        if (++pollTicks >= POLL_INTERVAL_TICKS) {
            pollTicks = 0;
            requestSnapshot();
        }
        if (lastSeenRevision != ClientAdminCache.revision()) {
            // The ~1/s poll rebuild recreates every widget and would wipe mid-typed text.
            String typedName = nameBox == null ? "" : nameBox.getValue();
            String typedLimit = limitBox == null ? "" : limitBox.getValue();
            boolean nameFocused = nameBox != null && nameBox.isFocused();
            boolean limitFocused = limitBox != null && limitBox.isFocused();
            rebuildWidgets();
            if (nameBox != null) {
                nameBox.setValue(typedName);
            }
            if (limitBox != null) {
                limitBox.setValue(typedLimit);
            }
            if (nameFocused && nameBox != null) {
                setFocused(nameBox);
            } else if (limitFocused && limitBox != null) {
                setFocused(limitBox);
            }
        }
    }

    private void requestSnapshot() {
        PacketDistributor.sendToServer(new ServerboundRequestAdminKitsSnapshotPayload());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, Component.literal("Edit Kit: " + kitId), width / 2, 16, PickerLayout.TITLE_COLOR);
        if (status != null) {
            graphics.drawCenteredString(font, status, width / 2, height - 40, 0xFFFFFFFF);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
