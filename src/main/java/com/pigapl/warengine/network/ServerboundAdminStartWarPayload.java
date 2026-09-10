package com.pigapl.warengine.network;

import com.pigapl.warengine.WarEngine;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ServerboundAdminStartWarPayload(int minutes) implements CustomPacketPayload {

    public static final Type<ServerboundAdminStartWarPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WarEngine.MODID, "admin_start_war"));

    public static final StreamCodec<ByteBuf, ServerboundAdminStartWarPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ServerboundAdminStartWarPayload::minutes,
            ServerboundAdminStartWarPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
