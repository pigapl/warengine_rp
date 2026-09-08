package com.pigapl.warengine.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** The capture zone's location/size, for the admin snapshot. {@code hasZone == false} => other fields are junk. */
public record AdminZone(boolean hasZone, String dim, int x, int y, int z, double radius) {

    public static final StreamCodec<ByteBuf, AdminZone> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, AdminZone::hasZone,
            ByteBufCodecs.STRING_UTF8, AdminZone::dim,
            ByteBufCodecs.VAR_INT, AdminZone::x,
            ByteBufCodecs.VAR_INT, AdminZone::y,
            ByteBufCodecs.VAR_INT, AdminZone::z,
            ByteBufCodecs.DOUBLE, AdminZone::radius,
            AdminZone::new
    );
}
