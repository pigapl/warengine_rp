package com.pigapl.warengine.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

/** One member of a squad, for the manage screen's kick and make-leader buttons. */
public record SquadMember(String name, UUID uuid, boolean leader) {

    public static final StreamCodec<ByteBuf, SquadMember> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, SquadMember::name,
            UUIDUtil.STREAM_CODEC, SquadMember::uuid,
            ByteBufCodecs.BOOL, SquadMember::leader,
            SquadMember::new
    );
}
