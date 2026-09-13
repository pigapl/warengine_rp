package com.pigapl.warengine.network;

import com.pigapl.warengine.WarEngine;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ServerboundAdminUpdateKitPayload(String kitId, String displayName)
        implements CustomPacketPayload {

    public static final Type<ServerboundAdminUpdateKitPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WarEngine.MODID, "admin_update_kit"));

    public static final StreamCodec<ByteBuf, ServerboundAdminUpdateKitPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ServerboundAdminUpdateKitPayload::kitId,
            ByteBufCodecs.STRING_UTF8, ServerboundAdminUpdateKitPayload::displayName,
            ServerboundAdminUpdateKitPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
