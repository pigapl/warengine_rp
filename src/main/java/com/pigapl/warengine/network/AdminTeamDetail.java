package com.pigapl.warengine.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

/**
 * One team's full detail row for the admin Teams &amp; Squads screen - color, display name, members,
 * and its base.
 *
 * @param baseSummary the team's base as {@code "x y z"} ({@code BaseService#describe}), or empty if
 *                    it has none - which also means that team has no kit-range restriction
 */
public record AdminTeamDetail(String team, int colorArgb, String displayName,
                              List<AdminPlayerInfo> members, String baseSummary) {

    public static final StreamCodec<ByteBuf, AdminTeamDetail> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, AdminTeamDetail::team,
            ByteBufCodecs.VAR_INT, AdminTeamDetail::colorArgb,
            ByteBufCodecs.STRING_UTF8, AdminTeamDetail::displayName,
            AdminPlayerInfo.STREAM_CODEC.apply(ByteBufCodecs.list()), AdminTeamDetail::members,
            ByteBufCodecs.STRING_UTF8, AdminTeamDetail::baseSummary,
            AdminTeamDetail::new
    );
}
