package com.pigapl.warengine.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

/** One capture-zone holder transition, for the admin panel's history log. {@code ""} team = empty/contested. */
public record AdminHistoryEntry(long epochMillis, String team, List<String> players) {

    public static final StreamCodec<ByteBuf, AdminHistoryEntry> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, AdminHistoryEntry::epochMillis,
            ByteBufCodecs.STRING_UTF8, AdminHistoryEntry::team,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), AdminHistoryEntry::players,
            AdminHistoryEntry::new
    );
}
