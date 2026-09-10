package com.pigapl.warengine.network;

import com.pigapl.warengine.WarEngine;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/** The server re-validates every reservation - see {@code SquadService#create}. */
public record ServerboundCreateSquadPayload(String name, int limit, Map<String, Integer> reservations)
        implements CustomPacketPayload {

    public static final Type<ServerboundCreateSquadPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WarEngine.MODID, "create_squad"));

    public static final StreamCodec<ByteBuf, ServerboundCreateSquadPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ServerboundCreateSquadPayload::name,
            ByteBufCodecs.VAR_INT, ServerboundCreateSquadPayload::limit,
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT),
                    ServerboundCreateSquadPayload::reservations,
            ServerboundCreateSquadPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
