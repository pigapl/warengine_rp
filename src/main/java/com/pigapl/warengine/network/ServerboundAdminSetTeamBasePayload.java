package com.pigapl.warengine.network;

import com.pigapl.warengine.WarEngine;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Admin panel's per-team "Base" button: sets that team's base to the ADMIN'S current position and
 * facing. No coordinates travel on the wire - the server reads them off the sender, so a modified
 * client cannot place a base somewhere it isn't standing.
 */
public record ServerboundAdminSetTeamBasePayload(String team) implements CustomPacketPayload {

    public static final Type<ServerboundAdminSetTeamBasePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WarEngine.MODID, "admin_set_team_base"));

    public static final StreamCodec<ByteBuf, ServerboundAdminSetTeamBasePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ServerboundAdminSetTeamBasePayload::team,
            ServerboundAdminSetTeamBasePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
