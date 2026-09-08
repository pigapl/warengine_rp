package com.pigapl.warengine.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * One kit as sent to a client for the kit-picker screen. {@code description} is {@code ""} when
 * unset. Full contents ({@link #loadout}) are included so the screen can preview the loadout
 * without a separate round-trip - see the client-UI protocol notes for why that trade is fine at
 * this kit count/size.
 */
public record KitCatalogEntry(String id, String displayName, ItemStack icon, String description,
                               KitLoadout loadout, KitAvailability availability) {

    public static final StreamCodec<RegistryFriendlyByteBuf, KitCatalogEntry> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, KitCatalogEntry::id,
            ByteBufCodecs.STRING_UTF8, KitCatalogEntry::displayName,
            ItemStack.OPTIONAL_STREAM_CODEC, KitCatalogEntry::icon,
            ByteBufCodecs.STRING_UTF8, KitCatalogEntry::description,
            KitLoadout.STREAM_CODEC, KitCatalogEntry::loadout,
            KitAvailability.STREAM_CODEC, KitCatalogEntry::availability,
            KitCatalogEntry::new
    );
}
