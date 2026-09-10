package com.pigapl.warengine.network;

import com.pigapl.warengine.WarEngine;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ServerboundAdminResetTicketsPayload() implements CustomPacketPayload {

    public static final Type<ServerboundAdminResetTicketsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WarEngine.MODID, "admin_reset_tickets"));

    public static final StreamCodec<ByteBuf, ServerboundAdminResetTicketsPayload> STREAM_CODEC =
            StreamCodec.unit(new ServerboundAdminResetTicketsPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
