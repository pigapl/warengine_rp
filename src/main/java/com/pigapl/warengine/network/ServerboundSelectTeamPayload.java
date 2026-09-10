package com.pigapl.warengine.network;

import com.pigapl.warengine.WarEngine;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ServerboundSelectTeamPayload(String teamId) implements CustomPacketPayload {

    public static final Type<ServerboundSelectTeamPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WarEngine.MODID, "select_team"));

    public static final StreamCodec<ByteBuf, ServerboundSelectTeamPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ServerboundSelectTeamPayload::teamId,
            ServerboundSelectTeamPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
