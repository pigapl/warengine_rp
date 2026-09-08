package com.pigapl.warengine.client.gui;

import com.pigapl.warengine.network.KitCatalogEntry;
import com.pigapl.warengine.network.ServerboundSelectKitPayload;
import com.pigapl.warengine.network.client.ClientKitCache;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Kit picker, drawn as a card grid. Reads {@link ClientKitCache#catalog()}, which the server pushes
 * on team assignment and refreshes whenever slot counts change, so this needs no request of its own.
 *
 * <p>A full kit is drawn disabled, but that is only convenience - {@code KitService.assign}
 * re-checks the limit server-side regardless of what the client believes.</p>
 */
public final class KitPickerScreen extends Screen {

    private static final int ACCENT = 0xFF5AC46A;   // your current kit
    private static final int ACCENT_FULL = 0xFFC45A5A;

    private PickerLayout.Grid grid;
    private int lastSeenRevision = -1;

    public KitPickerScreen() {
        super(Component.literal("Pick your Kit"));
    }

    /**
     * Rebuilds on a server push - otherwise a kit whose last slot a squad-mate just took keeps
     * rendering as clickable, and the click is rejected server-side for no visible reason.
     */
    @Override
    public void tick() {
        if (lastSeenRevision != ClientKitCache.revision()) {
            rebuildWidgets();
        }
    }

    @Override
    protected void init() {
        lastSeenRevision = ClientKitCache.revision();
        List<KitCatalogEntry> kits = ClientKitCache.catalog();
        addChangeSquadButton();
        if (kits.isEmpty()) {
            grid = null;
            return;
        }

        String currentKit = ClientKitCache.kitId();
        grid = PickerLayout.solve(kits.size(), width, height);

        for (int i = 0; i < kits.size(); i++) {
            KitCatalogEntry kit = kits.get(i);
            boolean isCurrent = kit.id().equals(currentKit);
            boolean full = kit.availability().full() && !isCurrent;

            SelectionCardWidget card = new SelectionCardWidget(
                    grid.xFor(i), grid.yFor(i), grid.cardWidth(), grid.cardHeight(),
                    Component.literal(kit.displayName()),
                    slotLabel(kit, isCurrent),
                    kit.icon(),
                    full ? ACCENT_FULL : ACCENT,
                    isCurrent,
                    () -> selectKit(kit.id()));
            card.active = !full;
            card.setTooltip(Tooltip.create(describe(kit)));
            addRenderableWidget(card);
        }
    }

    private static Component slotLabel(KitCatalogEntry kit, boolean isCurrent) {
        if (isCurrent) {
            return Component.literal("equipped");
        }
        if (kit.availability().unlimited()) {
            return Component.literal("open");
        }
        return Component.literal(kit.availability().taken() + " / " + kit.availability().limit());
    }

    /** Hover tooltip: the description plus what is actually in the kit. */
    private static Component describe(KitCatalogEntry kit) {
        List<String> lines = new ArrayList<>();
        if (!kit.description().isEmpty()) {
            lines.add(kit.description());
            lines.add("");
        }

        List<ItemStack> contents = new ArrayList<>();
        kit.loadout().armor().stream().filter(s -> !s.isEmpty()).forEach(contents::add);
        if (!kit.loadout().offhand().isEmpty()) {
            contents.add(kit.loadout().offhand());
        }
        contents.addAll(kit.loadout().inventory());

        int shown = Math.min(contents.size(), 10);
        for (int i = 0; i < shown; i++) {
            ItemStack stack = contents.get(i);
            lines.add((stack.getCount() > 1 ? stack.getCount() + "x " : "")
                    + stack.getHoverName().getString());
        }
        if (contents.size() > shown) {
            lines.add("... and " + (contents.size() - shown) + " more");
        }
        if (contents.isEmpty()) {
            lines.add("(empty kit)");
        }
        return Component.literal(String.join("\n", lines));
    }

    private void selectKit(String kitId) {
        PacketDistributor.sendToServer(new ServerboundSelectKitPayload(kitId));
        onClose();
    }

    /**
     * Way back to the squad picker. Without it the earlier steps are unreachable once you hold a kit,
     * since the keybind only opens whichever screen you currently owe - this one.
     */
    private void addChangeSquadButton() {
        int buttonWidth = 100;
        addRenderableWidget(Button.builder(Component.literal("Change Squad"),
                        b -> Minecraft.getInstance().setScreen(new SquadPickerScreen()))
                .bounds((width - buttonWidth) / 2, height - 46, buttonWidth, 20)
                .build());
    }

    /** Panel drawn here, not in render - {@code Screen.render} calls renderBackground itself, which
     *  would put the vanilla dim pass on top and darken the panel twice. */
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
                    Component.literal("No kits available for your team yet - ask an admin.")
                            .withStyle(ChatFormatting.GRAY),
                    width / 2, height / 2, PickerLayout.HINT_COLOR);
            return;
        }
        graphics.drawCenteredString(font,
                Component.literal("Hover a kit to see its contents"),
                width / 2, height - 18, PickerLayout.HINT_COLOR);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
