package com.pigapl.warengine.network;

import com.pigapl.warengine.WarEngine;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ServerboundAdminTeleportToPointPayload(String pointId) implements CustomPacketPayload {

    public static final Type<ServerboundAdminTeleportToPointPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WarEngine.MODID, "admin_tp_to_point"));

    public static final StreamCodec<ByteBuf, ServerboundAdminTeleportToPointPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, ServerboundAdminTeleportToPointPayload::pointId,
                    ServerboundAdminTeleportToPointPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
