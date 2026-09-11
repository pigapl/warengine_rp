package com.pigapl.warengine.client;

import com.pigapl.warengine.client.gui.AdminScreen;
import com.pigapl.warengine.client.gui.HudOptionsScreen;
import com.pigapl.warengine.client.gui.KitPickerScreen;
import com.pigapl.warengine.client.gui.SquadPickerScreen;
import com.pigapl.warengine.client.gui.TeamPickerScreen;
import com.pigapl.warengine.network.ServerboundSelectKitPayload;
import com.pigapl.warengine.network.client.ClientAdminCache;
import com.pigapl.warengine.network.client.ClientKitCache;
import com.pigapl.warengine.network.client.ClientSquadCache;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Drives all three pickers. The server never tells the client to open one - which screen is owed is
 * derived from state the client already has, as a {@code Team -> Squad -> Kit} chain. Each auto-opens
 * at most once per state, so closing one does not fight the player.
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

        // The HUD keeps drawing behind it - that is the point, you size it while looking at it.
        while (KeyBindings.HUD_OPTIONS.consumeClick()) {
            if (mc.screen == null) {
                mc.setScreen(new HudOptionsScreen(null));
            }
        }

        // Server-triggered, not derived from state, so it overrides whatever is open.
        if (ClientAdminCache.consumeOpenRequest()) {
            mc.setScreen(new AdminScreen());
        }

        ClientKitCache.PendingConfirm confirm = ClientKitCache.consumePendingConfirm();
        if (confirm != null) {
            mc.setScreen(new ConfirmScreen(yes -> {
                        if (yes) {
                            PacketDistributor.sendToServer(new ServerboundSelectKitPayload(confirm.kitId(), true));
                            mc.setScreen(null);
                        } else {
                            mc.setScreen(new KitPickerScreen());
                        }
                    },
                    Component.literal("Switch kit during the war?").withStyle(ChatFormatting.GOLD),
                    Component.literal(String.join("\n", confirm.lines())),
                    Component.literal("Switch anyway"),
                    Component.literal("Cancel")));
        }

        if (mc.player == null || mc.level == null) {
            autoOpenedTeamPicker = false;
            autoOpenedSquadPicker = false;
            autoOpenedKitPicker = false;
            return;
        }

        boolean hasTeam = mc.player.getTeam() != null;
        if (!hasTeam) {
            autoOpenedSquadPicker = false;
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
            autoOpenedKitPicker = false;
            // Wait for the list rather than flashing an empty screen. An empty list is still valid.
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
