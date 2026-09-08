package com.pigapl.warengine.network;

import com.pigapl.warengine.WarEngine;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client asks for a fresh scarce-weapon list - the admin Scarce Weapons screen polls this while open. */
public record ServerboundRequestAdminScarceSnapshotPayload() implements CustomPacketPayload {

    public static final Type<ServerboundRequestAdminScarceSnapshotPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WarEngine.MODID, "request_admin_scarce_snapshot"));

    public static final StreamCodec<ByteBuf, ServerboundRequestAdminScarceSnapshotPayload> STREAM_CODEC =
            StreamCodec.unit(new ServerboundRequestAdminScarceSnapshotPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
