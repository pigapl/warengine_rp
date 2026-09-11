package com.pigapl.warengine.network;

import com.pigapl.warengine.WarEngine;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** {@code confirmed} = the player already accepted the mid-war scarce-loss warning. */
public record ServerboundSelectKitPayload(String kitId, boolean confirmed) implements CustomPacketPayload {

    public static final Type<ServerboundSelectKitPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WarEngine.MODID, "select_kit"));

    public static final StreamCodec<ByteBuf, ServerboundSelectKitPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ServerboundSelectKitPayload::kitId,
            ByteBufCodecs.BOOL, ServerboundSelectKitPayload::confirmed,
            ServerboundSelectKitPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
