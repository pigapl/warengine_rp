package com.pigapl.warengine.network;

import com.pigapl.warengine.WarEngine;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * One confirm from the bulk screen: everything the admin ticked and everything they un-ticked. Sent by
 * identity rather than by index - unlike the one-at-a-time remove, the list the admin was looking at is
 * their own inventory or the kit items, neither of which lines up with the scarce list's order.
 */
public record ServerboundAdminBulkScarcePayload(List<ItemStack> scarce, List<ItemStack> notScarce)
        implements CustomPacketPayload {

    public static final Type<ServerboundAdminBulkScarcePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WarEngine.MODID, "admin_bulk_scarce"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundAdminBulkScarcePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list()), ServerboundAdminBulkScarcePayload::scarce,
                    ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list()), ServerboundAdminBulkScarcePayload::notScarce,
                    ServerboundAdminBulkScarcePayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
