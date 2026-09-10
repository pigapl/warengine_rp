package com.pigapl.warengine.network;

import com.pigapl.warengine.WarEngine;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record ClientboundAdminTeamsSnapshotPayload(List<AdminTeamDetail> teams, List<AdminSquadDetail> squads)
        implements CustomPacketPayload {

    public static final Type<ClientboundAdminTeamsSnapshotPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WarEngine.MODID, "admin_teams_snapshot"));

    public static final StreamCodec<ByteBuf, ClientboundAdminTeamsSnapshotPayload> STREAM_CODEC = StreamCodec.composite(
            AdminTeamDetail.STREAM_CODEC.apply(ByteBufCodecs.list()), ClientboundAdminTeamsSnapshotPayload::teams,
            AdminSquadDetail.STREAM_CODEC.apply(ByteBufCodecs.list()), ClientboundAdminTeamsSnapshotPayload::squads,
            ClientboundAdminTeamsSnapshotPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
