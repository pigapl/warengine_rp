package com.pigapl.warengine.network;

import com.pigapl.warengine.WarEngine;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Polled once a second while open, rather than the server tracking who has the screen open. */
public record ServerboundRequestAdminSnapshotPayload() implements CustomPacketPayload {

    public static final Type<ServerboundRequestAdminSnapshotPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WarEngine.MODID, "request_admin_snapshot"));

    public static final StreamCodec<ByteBuf, ServerboundRequestAdminSnapshotPayload> STREAM_CODEC =
            StreamCodec.unit(new ServerboundRequestAdminSnapshotPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
