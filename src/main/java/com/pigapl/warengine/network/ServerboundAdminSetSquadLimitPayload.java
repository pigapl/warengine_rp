package com.pigapl.warengine.network;

import com.pigapl.warengine.WarEngine;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Admin panel's set-squad-limit action - calls {@code SquadService#setLimit}. */
public record ServerboundAdminSetSquadLimitPayload(String squadId, int limit) implements CustomPacketPayload {

    public static final Type<ServerboundAdminSetSquadLimitPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WarEngine.MODID, "admin_set_squad_limit"));

    public static final StreamCodec<ByteBuf, ServerboundAdminSetSquadLimitPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ServerboundAdminSetSquadLimitPayload::squadId,
            ByteBufCodecs.VAR_INT, ServerboundAdminSetSquadLimitPayload::limit,
            ServerboundAdminSetSquadLimitPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
