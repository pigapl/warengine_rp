package com.pigapl.warengine.network;

import com.pigapl.warengine.WarEngine;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * A deliberate one-off exception to the "server never opens a screen" rule the pickers follow -
 * this is an explicit op-gated action, closer to a normal command.
 */
public record ClientboundOpenAdminScreenPayload() implements CustomPacketPayload {

    public static final Type<ClientboundOpenAdminScreenPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WarEngine.MODID, "open_admin_screen"));

    public static final StreamCodec<ByteBuf, ClientboundOpenAdminScreenPayload> STREAM_CODEC =
            StreamCodec.unit(new ClientboundOpenAdminScreenPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
