package com.pigapl.warengine.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

/**
 * Who holds the zone RIGHT NOW, computed fresh on every admin snapshot request from the same rule
 * {@code RoundService#tickCaptureZone} scores by (see {@code RoundService#zoneOccupantsByTeam}).
 *
 * @param holderTeam    the sole team present, or {@code ""} if empty/contested
 * @param holderPlayers that team's player names present, or (if contested) everyone present
 */
public record AdminZoneStatus(String holderTeam, List<String> holderPlayers, boolean contested) {

    public static final StreamCodec<ByteBuf, AdminZoneStatus> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, AdminZoneStatus::holderTeam,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), AdminZoneStatus::holderPlayers,
            ByteBufCodecs.BOOL, AdminZoneStatus::contested,
            AdminZoneStatus::new
    );
}
