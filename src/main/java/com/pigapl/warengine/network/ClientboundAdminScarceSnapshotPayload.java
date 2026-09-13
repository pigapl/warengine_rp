package com.pigapl.warengine.network;

import com.pigapl.warengine.WarEngine;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** {@code kitItems}: every distinct item used by any saved kit, so the bulk screen can list them. */
public record ClientboundAdminScarceSnapshotPayload(List<ItemStack> items, List<ItemStack> kitItems)
        implements CustomPacketPayload {

    public static final Type<ClientboundAdminScarceSnapshotPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WarEngine.MODID, "admin_scarce_snapshot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundAdminScarceSnapshotPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list()),
                    ClientboundAdminScarceSnapshotPayload::items,
                    ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list()),
                    ClientboundAdminScarceSnapshotPayload::kitItems,
                    ClientboundAdminScarceSnapshotPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
