package com.pigapl.warengine.network;

import com.pigapl.warengine.WarEngine;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** Toggles, rather than setting: the panel's button already shows the current state. */
public record ServerboundAdminToggleExemptPayload(UUID target) implements CustomPacketPayload {

    public static final Type<ServerboundAdminToggleExemptPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WarEngine.MODID, "admin_toggle_exempt"));

    public static final StreamCodec<ByteBuf, ServerboundAdminToggleExemptPayload> STREAM_CODEC =
            StreamCodec.composite(
                    UUIDUtil.STREAM_CODEC, ServerboundAdminToggleExemptPayload::target,
                    ServerboundAdminToggleExemptPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
