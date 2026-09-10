package com.pigapl.warengine.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

public final class KeyBindings {

    public static final KeyMapping OPEN_MENU = new KeyMapping(
            "key.warengine_pigapl_client.open_menu",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_N,
            "key.categories.warengine_pigapl_client"
    );

    public static final KeyMapping HUD_OPTIONS = new KeyMapping(
            "key.warengine_pigapl_client.hud_options",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_J,
            "key.categories.warengine_pigapl_client"
    );

    private KeyBindings() {}

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN_MENU);
        event.register(HUD_OPTIONS);
    }
}
