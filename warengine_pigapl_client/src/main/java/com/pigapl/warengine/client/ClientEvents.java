package com.pigapl.warengine.client;

import com.pigapl.warengine.client.gui.AdminScreen;
import com.pigapl.warengine.client.gui.KitPickerScreen;
import com.pigapl.warengine.client.gui.SquadPickerScreen;
import com.pigapl.warengine.client.gui.TeamPickerScreen;
import com.pigapl.warengine.network.client.ClientAdminCache;
import com.pigapl.warengine.network.client.ClientKitCache;
import com.pigapl.warengine.network.client.ClientSquadCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Drives all three pickers. The server never tells the client to open one - which screen is owed is
 * derived from state the client already has (scoreboard team, pushed squad id, pushed kit id):
 * "UI is a pure function of state", as a {@code Team -> Squad -> Kit} chain.
 *
 * <p>Each screen auto-opens at most once per state, so closing one does not fight the player. The
 * flag clears when its state changes, which is also what makes each hand-off work.</p>
 */
@EventBusSubscriber(modid = WarEngineClient.MODID, value = Dist.CLIENT)
public final class ClientEvents {

    private static boolean autoOpenedTeamPicker = false;
    private static boolean autoOpenedSquadPicker = false;
    private static boolean autoOpenedKitPicker = false;

    private ClientEvents() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();

        while (KeyBindings.OPEN_MENU.consumeClick()) {
            if (mc.screen == null) {
                mc.setScreen(owedScreen(mc));
            }
        }

        // Server-triggered (/warstate admin), not derived from state like the pickers, so it
        // overrides whatever is open. One-shot: the flag clears on read.
        if (ClientAdminCache.consumeOpenRequest()) {
            mc.setScreen(new AdminScreen());
        }

        if (mc.player == null || mc.level == null) {
            autoOpenedTeamPicker = false;
            autoOpenedSquadPicker = false;
            autoOpenedKitPicker = false;
            return;
        }

        boolean hasTeam = mc.player.getTeam() != null;
        if (!hasTeam) {
            autoOpenedSquadPicker = false; // so the squad picker can open once they do pick a team
            autoOpenedKitPicker = false;
            if (!autoOpenedTeamPicker && mc.screen == null) {
                autoOpenedTeamPicker = true;
                mc.setScreen(new TeamPickerScreen());
            }
            return;
        }
        autoOpenedTeamPicker = false;

        boolean hasSquad = !ClientSquadCache.squadId().isEmpty();
        if (!hasSquad) {
            autoOpenedKitPicker = false; // so the kit picker can open once they do pick a squad
            // Wait for the list rather than flashing an empty screen; the server sends it right after
            // the team assignment lands. An empty list is valid - the screen offers "Create Squad".
            if (!autoOpenedSquadPicker && mc.screen == null) {
                autoOpenedSquadPicker = true;
                mc.setScreen(new SquadPickerScreen());
            }
            return;
        }
        autoOpenedSquadPicker = false;

        if (ClientKitCache.kitId().isEmpty()) {
            // Same: wait for the catalog, sent right after the squad assignment lands.
            if (!autoOpenedKitPicker && mc.screen == null && !ClientKitCache.catalog().isEmpty()) {
                autoOpenedKitPicker = true;
                mc.setScreen(new KitPickerScreen());
            }
        } else {
            autoOpenedKitPicker = false;
        }
    }

    /** Whichever picker the player currently needs, following the Team -&gt; Squad -&gt; Kit chain. */
    private static Screen owedScreen(Minecraft mc) {
        if (mc.player == null || mc.player.getTeam() == null) {
            return new TeamPickerScreen();
        }
        if (ClientSquadCache.squadId().isEmpty()) {
            return new SquadPickerScreen();
        }
        return new KitPickerScreen();
    }
}
