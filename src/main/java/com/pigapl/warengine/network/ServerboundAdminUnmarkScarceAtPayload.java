package com.pigapl.warengine.network;

import com.pigapl.warengine.WarEngine;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Targets by index into the client's LAST snapshot, not by identity - a stale index on a race
 * removes nothing or the wrong-but-still-scarce item, never crashes.
 */
public record ServerboundAdminUnmarkScarceAtPayload(int index) implements CustomPacketPayload {

    public static final Type<ServerboundAdminUnmarkScarceAtPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WarEngine.MODID, "admin_unmark_scarce_at"));

    public static final StreamCodec<ByteBuf, ServerboundAdminUnmarkScarceAtPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ServerboundAdminUnmarkScarceAtPayload::index,
            ServerboundAdminUnmarkScarceAtPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
