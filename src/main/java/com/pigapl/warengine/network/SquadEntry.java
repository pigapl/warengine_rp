package com.pigapl.warengine.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * One squad as sent to a client for the squad-picker screen.
 *
 * @param members how many players are currently assigned - online or not, see
 *                {@code SquadService}'s class javadoc for why offline members still count
 * @param limit   max members this squad will ever accept, always {@code >= 1} - unlike a kit's
 *                limit there is no "unlimited" squad size
 */
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
