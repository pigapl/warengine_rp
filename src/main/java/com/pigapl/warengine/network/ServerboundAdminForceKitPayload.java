package com.pigapl.warengine.network;

import com.pigapl.warengine.WarEngine;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record ServerboundAdminForceKitPayload(UUID target, String kitId) implements CustomPacketPayload {

    public static final Type<ServerboundAdminForceKitPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WarEngine.MODID, "admin_force_kit"));

    public static final StreamCodec<ByteBuf, ServerboundAdminForceKitPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, ServerboundAdminForceKitPayload::target,
            ByteBufCodecs.STRING_UTF8, ServerboundAdminForceKitPayload::kitId,
            ServerboundAdminForceKitPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
