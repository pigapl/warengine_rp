package com.pigapl.warengine.client.gui;

import com.pigapl.warengine.network.KitBudgetEntry;
import com.pigapl.warengine.network.KitCountEntry;
import com.pigapl.warengine.network.ServerboundRequestKitBudgetPayload;
import com.pigapl.warengine.network.ServerboundSquadEditPayload;
import com.pigapl.warengine.network.ServerboundSquadMemberActionPayload;
import com.pigapl.warengine.network.ServerboundSquadReservePayload;
import com.pigapl.warengine.network.SquadEntry;
import com.pigapl.warengine.network.SquadMember;
import com.pigapl.warengine.network.client.ClientKitBudgetCache;
import com.pigapl.warengine.network.client.ClientSquadCache;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Where a commander or squad leader runs their squad: rename, resize, kick, appoint a leader, and move
 * reservations. A leader sees only the squad they lead, a commander every squad on the team - the
 * server decides that per squad and sends {@code canEdit}, this screen never works it out itself.
 *
 * <p>Reservations are editable at any time by the user's call, war or not. The round-start sweep reads
 * whatever the numbers are when it runs.</p>
 */
public final class SquadManageScreen extends Screen {

    private static final int ROW = 16;

    private final Screen parent;

    private String selectedId = "";
    private EditBox nameBox;
    private EditBox limitBox;
    private int lastSeenRevision = -1;
    private Button saveButton;

    public SquadManageScreen(Screen parent) {
        super(Component.literal("Manage Squad"));
        this.parent = parent;
    }

    private static List<SquadEntry> editable() {
        return ClientSquadCache.squads().stream().filter(SquadEntry::canEdit).toList();
    }

    private SquadEntry selected() {
        List<SquadEntry> list = editable();
        for (SquadEntry entry : list) {
            if (entry.id().equals(selectedId)) {
                return entry;
            }
        }
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    protected void init() {
        PacketDistributor.sendToServer(new ServerboundRequestKitBudgetPayload());
        lastSeenRevision = ClientSquadCache.revision();
        saveButton = null;

        int left = 20;
        int right = width - 20;
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
                .bounds(left, height - 28, 80, 20).build());

        SquadEntry squad = selected();
        if (squad == null) {
            return;
        }
        selectedId = squad.id();

        // A leader has exactly one squad, so the picker row only earns its space for a commander.
        List<SquadEntry> list = editable();
        int y = 30;
        if (list.size() > 1) {
            int x = left;
            for (SquadEntry entry : list) {
                int w = Math.max(50, font.width(entry.name()) + 12);
                Button tab = Button.builder(Component.literal(entry.name()), b -> {
                            selectedId = entry.id();
                            rebuildWidgets();
                        })
                        .bounds(x, y, w, 18).build();
                tab.active = !entry.id().equals(squad.id());
                addRenderableWidget(tab);
                x += w + 4;
            }
        }
        y = fieldsTop();

        nameBox = new EditBox(font, left, y, 140, 18, Component.literal("Squad name"));
        nameBox.setMaxLength(24);
        nameBox.setValue(squad.name());
        addRenderableWidget(nameBox);

        limitBox = new EditBox(font, left + 148, y, 50, 18, Component.literal("Max players"));
        limitBox.setMaxLength(3);
        limitBox.setValue(Integer.toString(squad.limit()));
        addRenderableWidget(limitBox);

        saveButton = addRenderableWidget(Button.builder(Component.literal("Save"), b -> save(squad.id()))
                .bounds(left + 202, y, 60, 18).build());

        int membersTop = membersTop();
        List<SquadMember> members = squad.manage().members();
        for (int i = 0; i < members.size() && membersTop + i * ROW < reservationsTop() - 18; i++) {
            SquadMember member = members.get(i);
            int rowY = membersTop + i * ROW;
            Button lead = Button.builder(Component.literal("Lead"),
                            b -> send(squad.id(), member.uuid(), ServerboundSquadMemberActionPayload.MAKE_LEADER))
                    .tooltip(Tooltip.create(Component.literal("Make this player the squad leader")))
                    .bounds(left + 150, rowY - 2, 40, 14).build();
            lead.active = !member.leader();
            addRenderableWidget(lead);
            addRenderableWidget(Button.builder(Component.literal("Kick").withStyle(ChatFormatting.RED),
                            b -> send(squad.id(), member.uuid(), ServerboundSquadMemberActionPayload.KICK))
                    .bounds(left + 194, rowY - 2, 40, 14).build());
            if (ClientSquadCache.commander() && !member.uuid().equals(Minecraft.getInstance().player.getUUID())) {
                addRenderableWidget(Button.builder(Component.literal("Command"),
                                b -> send(squad.id(), member.uuid(), ServerboundSquadMemberActionPayload.HAND_OVER))
                        .tooltip(Tooltip.create(Component.literal(
                                "Hand the commander role to this player. You stop being commander.")))
                        .bounds(left + 238, rowY - 2, 62, 14).build());
            }
        }

        int resTop = reservationsTop();
        List<KitCountEntry> reservations = squad.manage().reservations();
        for (int i = 0; i < reservations.size() && resTop + i * ROW < height - 34; i++) {
            KitCountEntry entry = reservations.get(i);
            int rowY = resTop + i * ROW;
            int max = entry.count() + remainingFor(entry.kitId());
            Button minus = Button.builder(Component.literal("-"),
                            b -> reserve(squad.id(), entry.kitId(), entry.count() - 1))
                    .bounds(right - 74, rowY - 2, 14, 14).build();
            minus.active = entry.count() > 0;
            addRenderableWidget(minus);
            Button plus = Button.builder(Component.literal("+"),
                            b -> reserve(squad.id(), entry.kitId(), entry.count() + 1))
                    .bounds(right - 24, rowY - 2, 14, 14).build();
            plus.active = entry.count() < max;
            addRenderableWidget(plus);
        }
    }

    // Read by BOTH init() (button placement) and render() (the text). They must never disagree -
    // computing them separately is exactly how the admin panel's rows once drifted off their buttons.
    private int fieldsTop() {
        return editable().size() > 1 ? 54 : 30;
    }

    private int membersTop() {
        return fieldsTop() + 36;   // clears the 18px field row plus the "Members" label above it
    }

    private int reservationsTop() {
        SquadEntry squad = selected();
        int members = squad == null ? 0 : squad.manage().members().size();
        return membersTop() + Math.max(1, members) * ROW + 24;
    }

    /** Team-wide unreserved budget, so a stepper cannot offer what another squad already holds. */
    private int remainingFor(String kitId) {
        for (KitBudgetEntry entry : ClientKitBudgetCache.entries()) {
            if (entry.kitId().equals(kitId)) {
                return entry.remaining();
            }
        }
        return 0;
    }

    private void save(String squadId) {
        int limit;
        try {
            limit = Integer.parseInt(limitBox.getValue().trim());
        } catch (NumberFormatException e) {
            return;
        }
        PacketDistributor.sendToServer(
                new ServerboundSquadEditPayload(squadId, nameBox.getValue().trim(), limit));
    }

    private void reserve(String squadId, String kitId, int count) {
        PacketDistributor.sendToServer(new ServerboundSquadReservePayload(squadId, kitId, count));
        PacketDistributor.sendToServer(new ServerboundRequestKitBudgetPayload());
    }

    private void send(String squadId, java.util.UUID target, String action) {
        PacketDistributor.sendToServer(new ServerboundSquadMemberActionPayload(squadId, target, action));
    }

    @Override
    public void tick() {
        if (lastSeenRevision != ClientSquadCache.revision()) {
            lastSeenRevision = ClientSquadCache.revision();
            // Same trap as the admin screens: a rebuild wipes whatever is being typed. Save and restore.
            String name = nameBox == null ? null : nameBox.getValue();
            String limit = limitBox == null ? null : limitBox.getValue();
            boolean nameFocused = nameBox != null && nameBox.isFocused();
            boolean limitFocused = limitBox != null && limitBox.isFocused();
            rebuildWidgets();
            if (nameBox != null && name != null) {
                nameBox.setValue(name);
            }
            if (limitBox != null && limit != null) {
                limitBox.setValue(limit);
            }
            if (nameFocused) {
                setFocused(nameBox);
            } else if (limitFocused) {
                setFocused(limitBox);
            }
        }
        updateSaveHighlight();
    }

    /**
     * Yellow Save = unsaved edits. Compared against what the server last sent, not what was typed at
     * open, so it clears by itself once the save lands and the list is pushed back.
     */
    private void updateSaveHighlight() {
        SquadEntry squad = selected();
        if (saveButton == null || squad == null || nameBox == null || limitBox == null) {
            return;
        }
        boolean dirty = !nameBox.getValue().trim().equals(squad.name())
                || !limitBox.getValue().trim().equals(Integer.toString(squad.limit()));
        saveButton.setMessage(dirty
                ? Component.literal("Save").withStyle(ChatFormatting.YELLOW)
                : Component.literal("Save"));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 12, PickerLayout.TITLE_COLOR);

        SquadEntry squad = selected();
        if (squad == null) {
            graphics.drawCenteredString(font,
                    Component.literal("No squad to manage").withStyle(ChatFormatting.GRAY),
                    width / 2, height / 2, PickerLayout.HINT_COLOR);
            return;
        }

        int left = 20;
        int membersTop = membersTop();
        graphics.drawString(font, "Members", left, membersTop - 14, 0xFFFFFFFF);
        List<SquadMember> members = squad.manage().members();
        if (members.isEmpty()) {
            graphics.drawString(font, "(empty)", left, membersTop, PickerLayout.HINT_COLOR);
        }
        for (int i = 0; i < members.size() && membersTop + i * ROW < reservationsTop() - 18; i++) {
            SquadMember member = members.get(i);
            graphics.drawString(font, member.name() + (member.leader() ? "  (leader)" : ""),
                    left, membersTop + i * ROW, member.leader() ? 0xFF5AC46A : 0xFFFFFFFF);
        }

        int resTop = reservationsTop();
        graphics.drawString(font, "Reserved kits", left, resTop - 14, 0xFFFFFFFF);
        List<KitCountEntry> reservations = squad.manage().reservations();
        if (reservations.isEmpty()) {
            graphics.drawString(font, "(this team has no kit budgets set)", left, resTop,
                    PickerLayout.HINT_COLOR);
        }
        for (int i = 0; i < reservations.size() && resTop + i * ROW < height - 34; i++) {
            KitCountEntry entry = reservations.get(i);
            graphics.drawString(font, entry.displayName(), left, resTop + i * ROW, 0xFFFFFFFF);
            graphics.drawCenteredString(font, Integer.toString(entry.count()),
                    width - 20 - 42, resTop + i * ROW, 0xFFFFFFFF);
        }
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
