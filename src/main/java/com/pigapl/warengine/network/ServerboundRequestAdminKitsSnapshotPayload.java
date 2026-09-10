package com.pigapl.warengine.network;

import com.pigapl.warengine.WarEngine;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ServerboundRequestAdminKitsSnapshotPayload() implements CustomPacketPayload {

    public static final Type<ServerboundRequestAdminKitsSnapshotPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WarEngine.MODID, "request_admin_kits_snapshot"));

    public static final StreamCodec<ByteBuf, ServerboundRequestAdminKitsSnapshotPayload> STREAM_CODEC =
            StreamCodec.unit(new ServerboundRequestAdminKitsSnapshotPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
