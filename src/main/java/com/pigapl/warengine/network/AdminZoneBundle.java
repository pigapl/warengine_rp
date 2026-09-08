package com.pigapl.warengine.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** {@link AdminZone} + {@link AdminZoneStatus} - split out purely to keep the top-level payload at 6 fields. */
public record AdminZoneBundle(AdminZone zone, AdminZoneStatus status) {

    public static final StreamCodec<ByteBuf, AdminZoneBundle> STREAM_CODEC = StreamCodec.composite(
            AdminZone.STREAM_CODEC, AdminZoneBundle::zone,
            AdminZoneStatus.STREAM_CODEC, AdminZoneBundle::status,
            AdminZoneBundle::new
    );
}
