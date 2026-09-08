package com.pigapl.warengine.network;

import com.pigapl.warengine.WarEngine;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Every {@code (team, kit)} budget row for the admin Kit Budgets screen - see {@link AdminBudgetRow}. */
public record ClientboundAdminBudgetSnapshotPayload(List<AdminBudgetRow> rows) implements CustomPacketPayload {

    public static final Type<ClientboundAdminBudgetSnapshotPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WarEngine.MODID, "admin_budget_snapshot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundAdminBudgetSnapshotPayload> STREAM_CODEC =
            StreamCodec.composite(
                    AdminBudgetRow.STREAM_CODEC.apply(ByteBufCodecs.list()), ClientboundAdminBudgetSnapshotPayload::rows,
                    ClientboundAdminBudgetSnapshotPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
