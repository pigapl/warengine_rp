package com.pigapl.warengine.network;

import com.pigapl.warengine.WarEngine;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Admin screen's Stop War button. The handler MUST re-check op status - see {@code AdminNetworking}. */
public record ServerboundAdminEndWarPayload() implements CustomPacketPayload {

    public static final Type<ServerboundAdminEndWarPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WarEngine.MODID, "admin_end_war"));

    public static final StreamCodec<ByteBuf, ServerboundAdminEndWarPayload> STREAM_CODEC =
            StreamCodec.unit(new ServerboundAdminEndWarPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
