package com.pigapl.warengine.network;

import com.pigapl.warengine.WarEngine;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client asks for a fresh budget snapshot - the admin Kit Budgets screen polls this while open. */
public record ServerboundRequestAdminBudgetSnapshotPayload() implements CustomPacketPayload {

    public static final Type<ServerboundRequestAdminBudgetSnapshotPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WarEngine.MODID, "request_admin_budget_snapshot"));

    public static final StreamCodec<ByteBuf, ServerboundRequestAdminBudgetSnapshotPayload> STREAM_CODEC =
            StreamCodec.unit(new ServerboundRequestAdminBudgetSnapshotPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
