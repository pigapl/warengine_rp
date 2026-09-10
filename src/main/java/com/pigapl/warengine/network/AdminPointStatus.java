package com.pigapl.warengine.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

public record AdminPointStatus(String owner, String capturing, int progress, int captureTotal,
                               boolean contested, List<String> occupants) {

    public static final StreamCodec<ByteBuf, AdminPointStatus> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, AdminPointStatus::owner,
            ByteBufCodecs.STRING_UTF8, AdminPointStatus::capturing,
            ByteBufCodecs.VAR_INT, AdminPointStatus::progress,
            ByteBufCodecs.VAR_INT, AdminPointStatus::captureTotal,
            ByteBufCodecs.BOOL, AdminPointStatus::contested,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), AdminPointStatus::occupants,
            AdminPointStatus::new
    );
}
