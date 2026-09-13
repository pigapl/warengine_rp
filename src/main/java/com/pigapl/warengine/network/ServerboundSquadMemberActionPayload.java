package com.pigapl.warengine.network;

import com.pigapl.warengine.WarEngine;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * One payload for the three "do this to that player" actions, since they share a shape. Unknown
 * actions are ignored server-side. {@link #HAND_OVER} ignores {@code squadId} - it moves the
 * commander role, which belongs to the team, not to a squad.
 */
public record ServerboundSquadMemberActionPayload(String squadId, UUID target, String action)
        implements CustomPacketPayload {

    public static final String KICK = "kick";
    public static final String MAKE_LEADER = "lead";
    public static final String HAND_OVER = "handover";

    public static final Type<ServerboundSquadMemberActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WarEngine.MODID, "squad_member_action"));

    public static final StreamCodec<ByteBuf, ServerboundSquadMemberActionPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, ServerboundSquadMemberActionPayload::squadId,
                    UUIDUtil.STREAM_CODEC, ServerboundSquadMemberActionPayload::target,
                    ByteBufCodecs.STRING_UTF8, ServerboundSquadMemberActionPayload::action,
                    ServerboundSquadMemberActionPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
