package com.pigapl.warengine.network;

import com.pigapl.warengine.WarEngine;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record ServerboundAdminSendToBasePayload(UUID target) implements CustomPacketPayload {

    public static final Type<ServerboundAdminSendToBasePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WarEngine.MODID, "admin_send_to_base"));

    public static final StreamCodec<ByteBuf, ServerboundAdminSendToBasePayload> STREAM_CODEC =
            UUIDUtil.STREAM_CODEC.map(ServerboundAdminSendToBasePayload::new,
                    ServerboundAdminSendToBasePayload::target);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
