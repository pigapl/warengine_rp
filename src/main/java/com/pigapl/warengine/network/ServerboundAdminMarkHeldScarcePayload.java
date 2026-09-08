package com.pigapl.warengine.network;

import com.pigapl.warengine.WarEngine;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Admin panel's "Mark held item as scarce" button - the UI trigger for {@code /kit scarce setscarce}. */
public record ServerboundAdminMarkHeldScarcePayload() implements CustomPacketPayload {

    public static final Type<ServerboundAdminMarkHeldScarcePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WarEngine.MODID, "admin_mark_held_scarce"));

    public static final StreamCodec<ByteBuf, ServerboundAdminMarkHeldScarcePayload> STREAM_CODEC =
            StreamCodec.unit(new ServerboundAdminMarkHeldScarcePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
