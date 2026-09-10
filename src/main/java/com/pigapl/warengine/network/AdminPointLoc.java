package com.pigapl.warengine.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Where a capture point is, for the admin snapshot. Split from {@link AdminPointStatus} because
 *  {@code StreamCodec.composite} tops out at six fields. */
public record AdminPointLoc(String id, String dim, int x, int y, int z, double radius) {

    public static final StreamCodec<ByteBuf, AdminPointLoc> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, AdminPointLoc::id,
            ByteBufCodecs.STRING_UTF8, AdminPointLoc::dim,
            ByteBufCodecs.VAR_INT, AdminPointLoc::x,
            ByteBufCodecs.VAR_INT, AdminPointLoc::y,
            ByteBufCodecs.VAR_INT, AdminPointLoc::z,
            ByteBufCodecs.DOUBLE, AdminPointLoc::radius,
            AdminPointLoc::new
    );
}
