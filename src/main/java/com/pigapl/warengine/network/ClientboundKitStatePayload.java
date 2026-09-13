package com.pigapl.warengine.network;

import com.pigapl.warengine.WarEngine;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** {@code reopenPicker} = the kit was taken AWAY (scarce sweep bump), not merely reported. */
public record ClientboundKitStatePayload(String kitId, boolean reopenPicker) implements CustomPacketPayload {

    public static final Type<ClientboundKitStatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WarEngine.MODID, "kit_state"));

    public static final StreamCodec<ByteBuf, ClientboundKitStatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ClientboundKitStatePayload::kitId,
            ByteBufCodecs.BOOL, ClientboundKitStatePayload::reopenPicker,
            ClientboundKitStatePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
