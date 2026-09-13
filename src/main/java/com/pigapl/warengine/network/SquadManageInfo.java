package com.pigapl.warengine.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

/**
 * The extra detail only someone who may EDIT this squad needs. Filled in per receiving player and
 * left empty for everyone else, so a plain member never learns a squad's roster or reservations from
 * the wire.
 */
public record SquadManageInfo(boolean canEdit, String leaderName, List<SquadMember> members,
                              List<KitCountEntry> reservations) {

    public static final SquadManageInfo NONE = new SquadManageInfo(false, "", List.of(), List.of());

    public static final StreamCodec<ByteBuf, SquadManageInfo> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, SquadManageInfo::canEdit,
            ByteBufCodecs.STRING_UTF8, SquadManageInfo::leaderName,
            SquadMember.STREAM_CODEC.apply(ByteBufCodecs.list()), SquadManageInfo::members,
            KitCountEntry.STREAM_CODEC.apply(ByteBufCodecs.list()), SquadManageInfo::reservations,
            SquadManageInfo::new
    );
}
