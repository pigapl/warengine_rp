package com.pigapl.warengine.network;

import com.pigapl.warengine.WarEngine;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** The player's team's budgeted kits, with totals and how much is still unclaimed - see {@link KitBudgetEntry}. */
public record ClientboundKitBudgetPayload(List<KitBudgetEntry> entries) implements CustomPacketPayload {

    public static final Type<ClientboundKitBudgetPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WarEngine.MODID, "kit_budget"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundKitBudgetPayload> STREAM_CODEC =
            StreamCodec.composite(
                    KitBudgetEntry.STREAM_CODEC.apply(ByteBufCodecs.list()), ClientboundKitBudgetPayload::entries,
                    ClientboundKitBudgetPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
