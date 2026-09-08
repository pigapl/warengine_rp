package com.pigapl.warengine.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

/** One squad's full detail row for the admin Teams &amp; Squads screen - roster, kits, and limit. */
public record AdminSquadDetail(String id, String team, String name, int limit, List<AdminPlayerInfo> members) {

    public static final StreamCodec<ByteBuf, AdminSquadDetail> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, AdminSquadDetail::id,
            ByteBufCodecs.STRING_UTF8, AdminSquadDetail::team,
            ByteBufCodecs.STRING_UTF8, AdminSquadDetail::name,
            ByteBufCodecs.VAR_INT, AdminSquadDetail::limit,
            AdminPlayerInfo.STREAM_CODEC.apply(ByteBufCodecs.list()), AdminSquadDetail::members,
            AdminSquadDetail::new
    );
}
