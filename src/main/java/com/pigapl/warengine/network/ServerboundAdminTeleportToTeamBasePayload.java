package com.pigapl.warengine.network;

import com.pigapl.warengine.WarEngine;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ServerboundAdminTeleportToTeamBasePayload(String team) implements CustomPacketPayload {

    public static final Type<ServerboundAdminTeleportToTeamBasePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WarEngine.MODID, "admin_tp_to_team_base"));

    public static final StreamCodec<ByteBuf, ServerboundAdminTeleportToTeamBasePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, ServerboundAdminTeleportToTeamBasePayload::team,
                    ServerboundAdminTeleportToTeamBasePayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
