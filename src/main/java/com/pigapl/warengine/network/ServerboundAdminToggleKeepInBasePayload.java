package com.pigapl.warengine.network;

import com.pigapl.warengine.WarEngine;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Flips the pre-war base lock. Toggles, since the event screen's button already shows the state. */
public record ServerboundAdminToggleKeepInBasePayload() implements CustomPacketPayload {

    public static final Type<ServerboundAdminToggleKeepInBasePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WarEngine.MODID, "admin_toggle_keep_in_base"));

    public static final StreamCodec<ByteBuf, ServerboundAdminToggleKeepInBasePayload> STREAM_CODEC =
            StreamCodec.unit(new ServerboundAdminToggleKeepInBasePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
