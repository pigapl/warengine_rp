package com.pigapl.warengine.client.gui;

import com.pigapl.warengine.network.ServerboundSelectSquadPayload;
import com.pigapl.warengine.network.SquadEntry;
import com.pigapl.warengine.network.client.ClientSquadCache;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

public final class SquadPickerScreen extends Screen {

    private static final int ACCENT = 0xFF5AC46A;   // your current squad
    private static final int ACCENT_FULL = 0xFFC45A5A;

    private PickerLayout.Grid grid;
    private int lastSeenRevision = -1;

    public SquadPickerScreen() {
        super(Component.literal("Pick your Squad"));
    }

    @Override
    public void tick() {
        if (lastSeenRevision != ClientSquadCache.revision()) {
            rebuildWidgets();
        }
    }

    @Override
    protected void init() {
        lastSeenRevision = ClientSquadCache.revision();
        List<SquadEntry> squads = ClientSquadCache.squads();
        addBackButton();
        addCreateButton();
        if (squads.isEmpty()) {
            grid = null;
            return;
        }

        String currentSquad = ClientSquadCache.squadId();
        grid = PickerLayout.solve(squads.size(), width, height);

        for (int i = 0; i < squads.size(); i++) {
            SquadEntry squad = squads.get(i);
            boolean isCurrent = squad.id().equals(currentSquad);
            boolean full = squad.full() && !isCurrent;

            SelectionCardWidget card = new SelectionCardWidget(
                    grid.xFor(i), grid.yFor(i), grid.cardWidth(), grid.cardHeight(),
                    Component.literal(squad.name()),
                    Component.literal(isCurrent ? "yours" : squad.members() + " / " + squad.limit()),
                    ItemStack.EMPTY,
                    full ? ACCENT_FULL : ACCENT,
                    isCurrent,
                    () -> selectSquad(squad.id()));
            card.active = !full;
            addRenderableWidget(card);
        }
    }

    private void selectSquad(String squadId) {
        PacketDistributor.sendToServer(new ServerboundSelectSquadPayload(squadId));
        onClose();
    }

    private void addBackButton() {
        int buttonWidth = 100;
        addRenderableWidget(Button.builder(Component.literal("Change Team"),
                        b -> Minecraft.getInstance().setScreen(new TeamPickerScreen()))
                .bounds((width - buttonWidth) / 2 - buttonWidth - 6, height - 46, buttonWidth, 20)
                .build());
    }

    private void addCreateButton() {
        int buttonWidth = 100;
        addRenderableWidget(Button.builder(Component.literal("Create Squad"),
                        b -> Minecraft.getInstance().setScreen(new CreateSquadScreen()))
                .bounds((width - buttonWidth) / 2 + buttonWidth + 6, height - 46, buttonWidth, 20)
                .build());
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        if (grid != null) {
            PickerLayout.drawPanel(graphics, grid);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(font, title, width / 2, 20, PickerLayout.TITLE_COLOR);

        if (grid == null) {
            graphics.drawCenteredString(font,
                    Component.literal("No squads yet on your team - create one below.")
                            .withStyle(ChatFormatting.GRAY),
                    width / 2, height / 2, PickerLayout.HINT_COLOR);
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
