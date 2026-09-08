package com.pigapl.warengine.network;

import com.pigapl.warengine.WarEngine;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** The current scarce-weapon list (normalised identities, as stored) for the admin Scarce Weapons screen. */
public record ClientboundAdminScarceSnapshotPayload(List<ItemStack> items) implements CustomPacketPayload {

    public static final Type<ClientboundAdminScarceSnapshotPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WarEngine.MODID, "admin_scarce_snapshot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundAdminScarceSnapshotPayload> STREAM_CODEC =
            ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list())
                    .map(ClientboundAdminScarceSnapshotPayload::new, ClientboundAdminScarceSnapshotPayload::items);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
