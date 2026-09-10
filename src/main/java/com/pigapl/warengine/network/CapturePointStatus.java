package com.pigapl.warengine.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * One capture point's live state, for the client HUD. Deliberately carries team NAMES and no colours:
 * the client already has the whole scoreboard synced, so it looks the colour up locally.
 */
public record CapturePointStatus(String id, String owner, String capturing, int progress,
                                 int captureTotal, boolean contested) {

    public static final StreamCodec<ByteBuf, CapturePointStatus> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, CapturePointStatus::id,
            ByteBufCodecs.STRING_UTF8, CapturePointStatus::owner,
            ByteBufCodecs.STRING_UTF8, CapturePointStatus::capturing,
            ByteBufCodecs.VAR_INT, CapturePointStatus::progress,
            ByteBufCodecs.VAR_INT, CapturePointStatus::captureTotal,
            ByteBufCodecs.BOOL, CapturePointStatus::contested,
            CapturePointStatus::new
    );
}
