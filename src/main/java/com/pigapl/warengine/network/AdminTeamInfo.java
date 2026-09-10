package com.pigapl.warengine.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

public record AdminTeamInfo(String team, int colorArgb, int tickets, List<String> onlinePlayers) {

    public static final StreamCodec<ByteBuf, AdminTeamInfo> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, AdminTeamInfo::team,
            ByteBufCodecs.VAR_INT, AdminTeamInfo::colorArgb,
            ByteBufCodecs.VAR_INT, AdminTeamInfo::tickets,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), AdminTeamInfo::onlinePlayers,
            AdminTeamInfo::new
    );
}
