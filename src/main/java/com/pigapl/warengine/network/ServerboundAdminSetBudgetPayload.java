package com.pigapl.warengine.network;

import com.pigapl.warengine.WarEngine;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ServerboundAdminSetBudgetPayload(String team, String kitId, int count) implements CustomPacketPayload {

    public static final Type<ServerboundAdminSetBudgetPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WarEngine.MODID, "admin_set_budget"));

    public static final StreamCodec<ByteBuf, ServerboundAdminSetBudgetPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ServerboundAdminSetBudgetPayload::team,
            ByteBufCodecs.STRING_UTF8, ServerboundAdminSetBudgetPayload::kitId,
            ByteBufCodecs.VAR_INT, ServerboundAdminSetBudgetPayload::count,
            ServerboundAdminSetBudgetPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
