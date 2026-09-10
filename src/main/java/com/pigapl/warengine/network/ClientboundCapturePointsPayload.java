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
 *
 * <p>{@code locations} carries real coordinates, so this is no longer safe-by-omission the way it
 * was. Added for the Xaero's map bridge; point positions are admin-placed and walked to anyway.</p>
 */
public record ClientboundCapturePointsPayload(List<CapturePointStatus> points,
                                              List<CapturePointLoc> locations,
                                              List<TeamTicketEntry> tickets, int ticketCap,
                                              boolean warActive, long timeLeftMillis)
        implements CustomPacketPayload {

    public static final Type<ClientboundCapturePointsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WarEngine.MODID, "capture_points"));

    // At composite's 6-field cap - anything more needs another split record.
    public static final StreamCodec<ByteBuf, ClientboundCapturePointsPayload> STREAM_CODEC = StreamCodec.composite(
            CapturePointStatus.STREAM_CODEC.apply(ByteBufCodecs.list()), ClientboundCapturePointsPayload::points,
            CapturePointLoc.STREAM_CODEC.apply(ByteBufCodecs.list()), ClientboundCapturePointsPayload::locations,
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
