package com.pigapl.warengine.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/** Full contents are included so the picker can preview a loadout without a second round-trip. */
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
