package com.pigapl.warengine.network;

import com.pigapl.warengine.WarEngine;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ServerboundRequestKitBudgetPayload() implements CustomPacketPayload {

    public static final Type<ServerboundRequestKitBudgetPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WarEngine.MODID, "request_kit_budget"));

    public static final StreamCodec<ByteBuf, ServerboundRequestKitBudgetPayload> STREAM_CODEC =
            StreamCodec.unit(new ServerboundRequestKitBudgetPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
