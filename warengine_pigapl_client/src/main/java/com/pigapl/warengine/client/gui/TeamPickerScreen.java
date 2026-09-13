package com.pigapl.warengine.client.gui;

import com.pigapl.warengine.network.ServerboundSelectTeamPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.scores.PlayerTeam;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

public final class TeamPickerScreen extends Screen {

    private static final int DEFAULT_ACCENT = 0xFF8A8A96;

    private PickerLayout.Grid grid;
    private String lastSeenSignature = null;

    public TeamPickerScreen() {
        super(Component.literal("Pick your Team"));
    }

    /** The scoreboard has no change event, so poll a cheap signature. */
    @Override
    public void tick() {
        if (!signature().equals(lastSeenSignature)) {
            rebuildWidgets();
        }
    }

    private String signature() {
        StringBuilder sb = new StringBuilder();
        for (PlayerTeam team : teams()) {
            sb.append(team.getName()).append('=').append(team.getPlayers().size()).append(';');
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getTeam() != null) {
            sb.append("own=").append(mc.player.getTeam().getName());
        }
        return sb.toString();
    }

    @Override
    protected void init() {
        lastSeenSignature = signature();
        List<PlayerTeam> teams = teams();
        if (teams.isEmpty()) {
            grid = null;
            return;
        }

        PlayerTeam ownTeam = Minecraft.getInstance().player == null ? null
                : (PlayerTeam) Minecraft.getInstance().player.getTeam();
        grid = PickerLayout.solve(teams.size(), width, height);

        for (int i = 0; i < teams.size(); i++) {
            PlayerTeam team = teams.get(i);
            boolean isCurrent = ownTeam != null && ownTeam.getName().equals(team.getName());
            int members = team.getPlayers().size();

            addRenderableWidget(new SelectionCardWidget(
                    grid.xFor(i), grid.yFor(i), grid.cardWidth(), grid.cardHeight(),
                    team.getFormattedDisplayName(),
                    Component.literal(isCurrent ? "your team"
                            : members + (members == 1 ? " player" : " players")),
                    ItemStack.EMPTY,
                    accentFor(team),
                    isCurrent,
                    () -> selectTeam(team.getName())));
        }
    }

    private static List<PlayerTeam> teams() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? List.of() : List.copyOf(mc.level.getScoreboard().getPlayerTeams());
    }

    private static int accentFor(PlayerTeam team) {
        ChatFormatting color = team.getColor();
        Integer rgb = color == null ? null : color.getColor();
        return rgb == null ? DEFAULT_ACCENT : 0xFF000000 | rgb;
    }

    private void selectTeam(String teamId) {
        PacketDistributor.sendToServer(new ServerboundSelectTeamPayload(teamId));
        Minecraft.getInstance().setScreen(new SquadPickerScreen(true));
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
        PickerLayout.drawSteps(graphics, font, width, PickerLayout.STEP_TEAM);

        if (grid == null) {
            graphics.drawCenteredString(font,
                    Component.literal("No teams have been set up yet - ask an admin.")
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
