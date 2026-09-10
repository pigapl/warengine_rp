package com.pigapl.warengine.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record AdminPointInfo(AdminPointLoc loc, AdminPointStatus status) {

    public static final StreamCodec<ByteBuf, AdminPointInfo> STREAM_CODEC = StreamCodec.composite(
            AdminPointLoc.STREAM_CODEC, AdminPointInfo::loc,
            AdminPointStatus.STREAM_CODEC, AdminPointInfo::status,
            AdminPointInfo::new
    );
}
