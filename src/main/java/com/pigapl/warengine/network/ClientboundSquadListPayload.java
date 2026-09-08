package com.pigapl.warengine.network;

import com.pigapl.warengine.WarEngine;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** The caller's current team's squads, sent whenever team membership or squad rosters change. */
public record ClientboundSquadListPayload(List<SquadEntry> squads) implements CustomPacketPayload {

    public static final Type<ClientboundSquadListPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WarEngine.MODID, "squad_list"));

    public static final StreamCodec<ByteBuf, ClientboundSquadListPayload> STREAM_CODEC =
            SquadEntry.STREAM_CODEC.apply(ByteBufCodecs.list())
                    .map(ClientboundSquadListPayload::new, ClientboundSquadListPayload::squads);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
