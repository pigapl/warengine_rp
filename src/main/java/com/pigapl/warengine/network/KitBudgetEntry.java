package com.pigapl.warengine.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

public record KitBudgetEntry(String kitId, String displayName, ItemStack icon, int total, int remaining) {

    public static final StreamCodec<RegistryFriendlyByteBuf, KitBudgetEntry> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, KitBudgetEntry::kitId,
            ByteBufCodecs.STRING_UTF8, KitBudgetEntry::displayName,
            ItemStack.OPTIONAL_STREAM_CODEC, KitBudgetEntry::icon,
            ByteBufCodecs.VAR_INT, KitBudgetEntry::total,
            ByteBufCodecs.VAR_INT, KitBudgetEntry::remaining,
            KitBudgetEntry::new
    );
}
