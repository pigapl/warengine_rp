package com.pigapl.warengine.network;

import com.pigapl.warengine.WarEngine;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client asks for a fresh teams/squads snapshot - the admin Teams &amp; Squads screen polls this while open. */
public record ServerboundRequestAdminTeamsSnapshotPayload() implements CustomPacketPayload {

    public static final Type<ServerboundRequestAdminTeamsSnapshotPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WarEngine.MODID, "request_admin_teams_snapshot"));

    public static final StreamCodec<ByteBuf, ServerboundRequestAdminTeamsSnapshotPayload> STREAM_CODEC =
            StreamCodec.unit(new ServerboundRequestAdminTeamsSnapshotPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
