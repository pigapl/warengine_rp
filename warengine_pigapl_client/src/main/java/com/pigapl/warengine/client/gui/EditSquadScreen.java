package com.pigapl.warengine.client.gui;

import com.pigapl.warengine.network.AdminSquadDetail;
import com.pigapl.warengine.network.ClientboundAdminTeamsSnapshotPayload;
import com.pigapl.warengine.network.ServerboundAdminRenameSquadPayload;
import com.pigapl.warengine.network.ServerboundAdminSetSquadLimitPayload;
import com.pigapl.warengine.network.ServerboundRequestAdminTeamsSnapshotPayload;
import com.pigapl.warengine.network.client.ClientAdminCache;
import com.pigapl.warengine.squad.SquadService;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public final class EditSquadScreen extends Screen {

    private static final int FIELD_WIDTH = 180;
    private static final int POLL_INTERVAL_TICKS = 20;

    private final String squadId;
    private EditBox nameBox;
    private EditBox limitBox;
    private int pollTicks = 0;
    private int lastSeenRevision = -1;
    private Component status = null;

    public EditSquadScreen(String squadId) {
        super(Component.literal("Edit Squad"));
        this.squadId = squadId;
    }

    private AdminSquadDetail find() {
        ClientboundAdminTeamsSnapshotPayload snap = ClientAdminCache.teamsSnapshot();
        if (snap == null) {
            return null;
        }
        for (AdminSquadDetail s : snap.squads()) {
            if (s.id().equals(squadId)) {
                return s;
            }
        }
        return null;
    }

    @Override
    protected void init() {
        requestSnapshot();
        lastSeenRevision = ClientAdminCache.revision();

        AdminSquadDetail squad = find();
        int centerX = width / 2;
        int top = height / 2 - 40;

        nameBox = new EditBox(font, centerX - FIELD_WIDTH / 2, top, FIELD_WIDTH, 20, Component.literal("Squad name"));
        nameBox.setMaxLength(SquadService.NAME_MAX_LENGTH);
        nameBox.setValue(squad == null ? "" : squad.name());
        addRenderableWidget(nameBox);
        setInitialFocus(nameBox);

        limitBox = new EditBox(font, centerX - FIELD_WIDTH / 2, top + 24, FIELD_WIDTH, 20, Component.literal("Limit"));
        limitBox.setMaxLength(3);
        limitBox.setFilter(s -> s.isEmpty() || s.chars().allMatch(Character::isDigit));
        limitBox.setValue(squad == null ? "" : Integer.toString(squad.limit()));
        addRenderableWidget(limitBox);

        int buttonWidth = 90;
        addRenderableWidget(Button.builder(Component.literal("Save"), b -> save())
                .bounds(centerX - buttonWidth - 4, top + 50, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"),
                        b -> Minecraft.getInstance().setScreen(new AdminTeamsScreen()))
                .bounds(centerX + 4, top + 50, buttonWidth, 20).build());
    }

    private void save() {
        String name = nameBox.getValue().trim();
        if (!name.isEmpty()) {
            PacketDistributor.sendToServer(new ServerboundAdminRenameSquadPayload(squadId, name));
        }
        try {
            int limit = Integer.parseInt(limitBox.getValue().trim());
            PacketDistributor.sendToServer(new ServerboundAdminSetSquadLimitPayload(squadId, limit));
        } catch (NumberFormatException ignored) {
        }
        status = Component.literal("Saved.").withStyle(ChatFormatting.GREEN);
    }

    @Override
    public void tick() {
        if (++pollTicks >= POLL_INTERVAL_TICKS) {
            pollTicks = 0;
            requestSnapshot();
        }
        if (lastSeenRevision != ClientAdminCache.revision()) {
            // Same wipe-while-typing bug as EditKitScreen - see its tick() comment.
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
        PacketDistributor.sendToServer(new ServerboundRequestAdminTeamsSnapshotPayload());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, Component.literal("Edit Squad: " + squadId),
                width / 2, height / 2 - 40 - 18, PickerLayout.TITLE_COLOR);
        if (status != null) {
            graphics.drawCenteredString(font, status, width / 2, height / 2 + 34, 0xFFFFFFFF);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
