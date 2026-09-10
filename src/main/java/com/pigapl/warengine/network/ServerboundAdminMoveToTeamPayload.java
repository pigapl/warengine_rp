package com.pigapl.warengine.network;

import com.pigapl.warengine.WarEngine;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record ServerboundAdminMoveToTeamPayload(UUID target, String team) implements CustomPacketPayload {

    public static final Type<ServerboundAdminMoveToTeamPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WarEngine.MODID, "admin_move_to_team"));

    public static final StreamCodec<ByteBuf, ServerboundAdminMoveToTeamPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, ServerboundAdminMoveToTeamPayload::target,
            ByteBufCodecs.STRING_UTF8, ServerboundAdminMoveToTeamPayload::team,
            ServerboundAdminMoveToTeamPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
