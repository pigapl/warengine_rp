package com.pigapl.warengine.network;

import com.pigapl.warengine.WarEngine;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Admin panel's Delete Team button - calls {@code TeamKits#removeTeam}. */
public record ServerboundAdminDeleteTeamPayload(String id) implements CustomPacketPayload {

    public static final Type<ServerboundAdminDeleteTeamPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WarEngine.MODID, "admin_delete_team"));

    public static final StreamCodec<ByteBuf, ServerboundAdminDeleteTeamPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ServerboundAdminDeleteTeamPayload::id,
            ServerboundAdminDeleteTeamPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
