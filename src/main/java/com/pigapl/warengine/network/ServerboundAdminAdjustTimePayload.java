package com.pigapl.warengine.network;

import com.pigapl.warengine.WarEngine;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Admin panel's +/- minute buttons - extends or shortens a running war's clock. No-op if none is running. */
public record ServerboundAdminAdjustTimePayload(int deltaMinutes) implements CustomPacketPayload {

    public static final Type<ServerboundAdminAdjustTimePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WarEngine.MODID, "admin_adjust_time"));

    public static final StreamCodec<ByteBuf, ServerboundAdminAdjustTimePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ServerboundAdminAdjustTimePayload::deltaMinutes,
            ServerboundAdminAdjustTimePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
