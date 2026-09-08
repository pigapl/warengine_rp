package com.pigapl.warengine.network;

import com.pigapl.warengine.WarEngine;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client asks for its team's kit budgets - the squad-picker's Create screen requests this on open so
 * its "reserve N / M" steppers know each budgeted kit's total and how much is still unclaimed.
 */
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
