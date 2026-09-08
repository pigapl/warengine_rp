package com.pigapl.warengine.client;

import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * War Engine Client - client-only addon (UI/HUD) for the {@code warengine_pigapl} base mod, which it
 * requires for the shared state it reads. {@code dist = Dist.CLIENT} means it never initializes on a
 * dedicated server even if the jar is present there.
 */
@Mod(value = WarEngineClient.MODID, dist = Dist.CLIENT)
public final class WarEngineClient {
    public static final String MODID = "warengine_pigapl_client";
    public static final Logger LOGGER = LogUtils.getLogger();

    public WarEngineClient(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(KeyBindings::register);
        LOGGER.info("War Engine Client {} loaded", modContainer.getModInfo().getVersion());
    }
}
