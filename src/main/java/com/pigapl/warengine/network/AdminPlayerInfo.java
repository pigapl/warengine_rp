package com.pigapl.warengine.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

/**
 * One player's row in a team or squad detail list, for the admin panel.
 *
 * @param kitId their assigned kit id, or {@code ""} for none
 */
public record AdminPlayerInfo(String name, UUID uuid, boolean online, String kitId) {

    public static final StreamCodec<ByteBuf, AdminPlayerInfo> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, AdminPlayerInfo::name,
            UUIDUtil.STREAM_CODEC, AdminPlayerInfo::uuid,
            ByteBufCodecs.BOOL, AdminPlayerInfo::online,
            ByteBufCodecs.STRING_UTF8, AdminPlayerInfo::kitId,
            AdminPlayerInfo::new
    );
}
