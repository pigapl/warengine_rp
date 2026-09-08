package com.pigapl.warengine.network;

import com.pigapl.warengine.WarEngine;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Admin panel's "TP to Zone" button. No-op if no zone is set. */
public record ServerboundAdminTeleportToZonePayload() implements CustomPacketPayload {

    public static final Type<ServerboundAdminTeleportToZonePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WarEngine.MODID, "admin_tp_to_zone"));

    public static final StreamCodec<ByteBuf, ServerboundAdminTeleportToZonePayload> STREAM_CODEC =
            StreamCodec.unit(new ServerboundAdminTeleportToZonePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
