package com.pigapl.warengine.network;

import com.pigapl.warengine.WarEngine;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * An absolute count, not a delta - two people nudging the same stepper can then only disagree, never
 * compound. Allowed mid-war by the user's call; the whistle sweep reads these numbers when it runs.
 */
public record ServerboundSquadReservePayload(String squadId, String kitId, int count)
        implements CustomPacketPayload {

    public static final Type<ServerboundSquadReservePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WarEngine.MODID, "squad_reserve"));

    public static final StreamCodec<ByteBuf, ServerboundSquadReservePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ServerboundSquadReservePayload::squadId,
            ByteBufCodecs.STRING_UTF8, ServerboundSquadReservePayload::kitId,
            ByteBufCodecs.VAR_INT, ServerboundSquadReservePayload::count,
            ServerboundSquadReservePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
