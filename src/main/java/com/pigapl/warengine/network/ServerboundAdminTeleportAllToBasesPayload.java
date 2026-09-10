package com.pigapl.warengine.network;

import com.pigapl.warengine.WarEngine;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ServerboundAdminTeleportAllToBasesPayload() implements CustomPacketPayload {

    public static final Type<ServerboundAdminTeleportAllToBasesPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WarEngine.MODID, "admin_tp_all_to_bases"));

    public static final StreamCodec<ByteBuf, ServerboundAdminTeleportAllToBasesPayload> STREAM_CODEC =
            StreamCodec.unit(new ServerboundAdminTeleportAllToBasesPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
