package com.pigapl.warengine.network;

import com.pigapl.warengine.WarEngine;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * A full live snapshot of the war for the admin panel - sent in reply to
 * {@link ServerboundRequestAdminSnapshotPayload}. Op-gated server-side (payload handlers get no
 * automatic permission check the way a Brigadier command does - see {@code AdminNetworking}).
 */
public record ClientboundAdminSnapshotPayload(boolean warActive, long timeLeftMillis, int ticketCap,
                                               List<AdminTeamInfo> teams, AdminZoneBundle zoneBundle,
                                               List<AdminHistoryEntry> history) implements CustomPacketPayload {

    public static final Type<ClientboundAdminSnapshotPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WarEngine.MODID, "admin_snapshot"));

    public static final StreamCodec<ByteBuf, ClientboundAdminSnapshotPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, ClientboundAdminSnapshotPayload::warActive,
            ByteBufCodecs.VAR_LONG, ClientboundAdminSnapshotPayload::timeLeftMillis,
            ByteBufCodecs.VAR_INT, ClientboundAdminSnapshotPayload::ticketCap,
            AdminTeamInfo.STREAM_CODEC.apply(ByteBufCodecs.list()), ClientboundAdminSnapshotPayload::teams,
            AdminZoneBundle.STREAM_CODEC, ClientboundAdminSnapshotPayload::zoneBundle,
            AdminHistoryEntry.STREAM_CODEC.apply(ByteBufCodecs.list()), ClientboundAdminSnapshotPayload::history,
            ClientboundAdminSnapshotPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
