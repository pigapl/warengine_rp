package com.pigapl.warengine;

import com.mojang.logging.LogUtils;
import com.pigapl.warengine.network.AdminNetworking;
import com.pigapl.warengine.network.KitNetworking;
import com.pigapl.warengine.network.SquadNetworking;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

/**
 * War Engine - event running tools for weekly modded combat events.
 *
 * <p>The UI addon ({@code warengine_pigapl_client}) needs this mod on the player's client too, for
 * {@link KitNetworking}'s payloads. Gameplay logic stays server-authoritative either way.</p>
 */
@Mod(WarEngine.MODID)
public final class WarEngine {
    public static final String MODID = "warengine_pigapl";
    public static final Logger LOGGER = LogUtils.getLogger();

    public WarEngine(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.SERVER, WarConfig.SPEC);
        modEventBus.addListener(KitNetworking::register);
        modEventBus.addListener(SquadNetworking::register);
        modEventBus.addListener(AdminNetworking::register);
        // Game-bus event handlers live in GameEvents (@EventBusSubscriber). Nothing else to wire here yet.
        LOGGER.info("War Engine {} loaded", modContainer.getModInfo().getVersion());
    }
}
