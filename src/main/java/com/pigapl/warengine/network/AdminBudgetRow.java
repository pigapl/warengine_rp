package com.pigapl.warengine.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * One {@code (team, kit)} row in the admin Kit Budgets screen: every kit each team may use, with its
 * current team-wide budget ({@code total}, 0 = none set) and how much its squads have already
 * reserved. The screen groups these by team.
 */
public record AdminBudgetRow(String team, String kitId, String displayName, ItemStack icon,
                             int total, int reserved) {

    public static final StreamCodec<RegistryFriendlyByteBuf, AdminBudgetRow> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, AdminBudgetRow::team,
            ByteBufCodecs.STRING_UTF8, AdminBudgetRow::kitId,
            ByteBufCodecs.STRING_UTF8, AdminBudgetRow::displayName,
            ItemStack.OPTIONAL_STREAM_CODEC, AdminBudgetRow::icon,
            ByteBufCodecs.VAR_INT, AdminBudgetRow::total,
            ByteBufCodecs.VAR_INT, AdminBudgetRow::reserved,
            AdminBudgetRow::new
    );
}
