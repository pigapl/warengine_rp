package com.pigapl.warengine.network;

import com.pigapl.warengine.WarEngine;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Admin Kit Library screen's per-team toggle - assigns the kit to {@code team} if not mapped, else unassigns it. */
public record ServerboundAdminToggleKitTeamPayload(String kitId, String team) implements CustomPacketPayload {

    public static final Type<ServerboundAdminToggleKitTeamPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WarEngine.MODID, "admin_toggle_kit_team"));

    public static final StreamCodec<ByteBuf, ServerboundAdminToggleKitTeamPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ServerboundAdminToggleKitTeamPayload::kitId,
            ByteBufCodecs.STRING_UTF8, ServerboundAdminToggleKitTeamPayload::team,
            ServerboundAdminToggleKitTeamPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
