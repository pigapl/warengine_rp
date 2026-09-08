package com.pigapl.warengine.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** The actual item contents of a kit, split out of {@link KitCatalogEntry} - StreamCodec.composite tops out at 6 fields. */
public record KitLoadout(List<ItemStack> armor, ItemStack offhand, List<ItemStack> inventory) {

    public static final StreamCodec<RegistryFriendlyByteBuf, KitLoadout> STREAM_CODEC = StreamCodec.composite(
            ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list()), KitLoadout::armor,
            ItemStack.OPTIONAL_STREAM_CODEC, KitLoadout::offhand,
            ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list()), KitLoadout::inventory,
            KitLoadout::new
    );
}
