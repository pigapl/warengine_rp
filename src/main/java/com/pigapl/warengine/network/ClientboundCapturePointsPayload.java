package com.pigapl.warengine.network;

import com.pigapl.warengine.WarEngine;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Broadcast to EVERY player - not op-gated, which is why the HUD cannot reuse the admin snapshot.
 * Pushed on change rather than polled, so a quiet map costs nothing.
 */
public record ClientboundCapturePointsPayload(List<CapturePointStatus> points,
                                              List<TeamTicketEntry> tickets, int ticketCap,
                                              boolean warActive, long timeLeftMillis)
        implements CustomPacketPayload {

    public static final Type<ClientboundCapturePointsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WarEngine.MODID, "capture_points"));

    public static final StreamCodec<ByteBuf, ClientboundCapturePointsPayload> STREAM_CODEC = StreamCodec.composite(
            CapturePointStatus.STREAM_CODEC.apply(ByteBufCodecs.list()), ClientboundCapturePointsPayload::points,
            TeamTicketEntry.STREAM_CODEC.apply(ByteBufCodecs.list()), ClientboundCapturePointsPayload::tickets,
            ByteBufCodecs.VAR_INT, ClientboundCapturePointsPayload::ticketCap,
            ByteBufCodecs.BOOL, ClientboundCapturePointsPayload::warActive,
            ByteBufCodecs.VAR_LONG, ClientboundCapturePointsPayload::timeLeftMillis,
            ClientboundCapturePointsPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
