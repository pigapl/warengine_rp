package com.pigapl.warengine.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

/** {@code farFromBase}: metres from own base when outside the kit radius before a war, else -1. */
public record AdminPlayerInfo(String name, UUID uuid, boolean online, String kitId, int farFromBase) {

    public static final StreamCodec<ByteBuf, AdminPlayerInfo> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, AdminPlayerInfo::name,
            UUIDUtil.STREAM_CODEC, AdminPlayerInfo::uuid,
            ByteBufCodecs.BOOL, AdminPlayerInfo::online,
            ByteBufCodecs.STRING_UTF8, AdminPlayerInfo::kitId,
            ByteBufCodecs.VAR_INT, AdminPlayerInfo::farFromBase,
            AdminPlayerInfo::new
    );
}
