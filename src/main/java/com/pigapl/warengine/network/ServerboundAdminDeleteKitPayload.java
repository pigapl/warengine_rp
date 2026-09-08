package com.pigapl.warengine.network;

import com.pigapl.warengine.WarEngine;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Admin Kit Library screen's Delete button - calls {@code KitStorage#delete} + drops it from every team mapping. */
public record ServerboundAdminDeleteKitPayload(String kitId) implements CustomPacketPayload {

    public static final Type<ServerboundAdminDeleteKitPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WarEngine.MODID, "admin_delete_kit"));

    public static final StreamCodec<ByteBuf, ServerboundAdminDeleteKitPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ServerboundAdminDeleteKitPayload::kitId,
            ServerboundAdminDeleteKitPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
