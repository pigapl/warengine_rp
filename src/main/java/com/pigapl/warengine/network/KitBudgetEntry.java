package com.pigapl.warengine.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * One budgeted kit as offered to a squad creator on the squad-picker's "reserve" step - the
 * "press 2/5" row. Only kits the player's team has a {@code TeamKits} budget for are sent.
 *
 * @param total     the team's whole budget for this kit
 * @param remaining how much of it is still unclaimed by existing squads - the ceiling on what this
 *                  new squad may reserve
 */
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
