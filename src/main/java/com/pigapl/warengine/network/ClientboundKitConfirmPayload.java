package com.pigapl.warengine.network;

import com.pigapl.warengine.WarEngine;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** "Picking this kit mid-war costs you scarce items" - the client answers by re-sending the pick confirmed. */
public record ClientboundKitConfirmPayload(String kitId, List<String> lines) implements CustomPacketPayload {

    public static final Type<ClientboundKitConfirmPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WarEngine.MODID, "kit_confirm"));

    public static final StreamCodec<ByteBuf, ClientboundKitConfirmPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ClientboundKitConfirmPayload::kitId,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), ClientboundKitConfirmPayload::lines,
            ClientboundKitConfirmPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
