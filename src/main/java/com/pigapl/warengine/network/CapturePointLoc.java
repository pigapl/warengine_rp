package com.pigapl.warengine.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Where a point is, for the client map bridge. Separate from {@link CapturePointStatus} because that
 * one is already at StreamCodec.composite's 6-field cap.
 */
public record CapturePointLoc(String id, String dim, int x, int y, int z, int radius) {

    public static final StreamCodec<ByteBuf, CapturePointLoc> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, CapturePointLoc::id,
            ByteBufCodecs.STRING_UTF8, CapturePointLoc::dim,
            ByteBufCodecs.VAR_INT, CapturePointLoc::x,
            ByteBufCodecs.VAR_INT, CapturePointLoc::y,
            ByteBufCodecs.VAR_INT, CapturePointLoc::z,
            ByteBufCodecs.VAR_INT, CapturePointLoc::radius,
            CapturePointLoc::new
    );
}
