package com.pigapl.warengine.network;

import com.pigapl.warengine.WarEngine;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record ClientboundAdminKitsSnapshotPayload(List<AdminKitInfo> kits, List<String> teamIds)
        implements CustomPacketPayload {

    public static final Type<ClientboundAdminKitsSnapshotPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WarEngine.MODID, "admin_kits_snapshot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundAdminKitsSnapshotPayload> STREAM_CODEC =
            StreamCodec.composite(
                    AdminKitInfo.STREAM_CODEC.apply(ByteBufCodecs.list()), ClientboundAdminKitsSnapshotPayload::kits,
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), ClientboundAdminKitsSnapshotPayload::teamIds,
                    ClientboundAdminKitsSnapshotPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
