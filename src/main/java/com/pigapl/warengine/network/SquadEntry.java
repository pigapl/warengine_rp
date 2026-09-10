package com.pigapl.warengine.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record SquadEntry(String id, String name, int members, int limit) {

    public static final StreamCodec<ByteBuf, SquadEntry> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, SquadEntry::id,
            ByteBufCodecs.STRING_UTF8, SquadEntry::name,
            ByteBufCodecs.VAR_INT, SquadEntry::members,
            ByteBufCodecs.VAR_INT, SquadEntry::limit,
            SquadEntry::new
    );

    public boolean full() {
        return members >= limit;
    }
}
