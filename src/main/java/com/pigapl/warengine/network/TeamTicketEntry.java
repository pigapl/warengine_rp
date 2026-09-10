package com.pigapl.warengine.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** One team's ticket count for the HUD scoreboard. A record list, not a map, to keep wire order stable. */
public record TeamTicketEntry(String team, int tickets) {

    public static final StreamCodec<ByteBuf, TeamTicketEntry> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, TeamTicketEntry::team,
            ByteBufCodecs.VAR_INT, TeamTicketEntry::tickets,
            TeamTicketEntry::new
    );
}
