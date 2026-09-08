package com.pigapl.warengine.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** One kit's row in the admin Kit Library screen - the whole library, not team/squad scoped like {@link KitCatalogEntry}. */
public record AdminKitInfo(String id, String displayName, ItemStack icon, int limit, List<String> assignedTeams) {

    public static final StreamCodec<RegistryFriendlyByteBuf, AdminKitInfo> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, AdminKitInfo::id,
            ByteBufCodecs.STRING_UTF8, AdminKitInfo::displayName,
            ItemStack.OPTIONAL_STREAM_CODEC, AdminKitInfo::icon,
            ByteBufCodecs.VAR_INT, AdminKitInfo::limit,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), AdminKitInfo::assignedTeams,
            AdminKitInfo::new
    );
}
