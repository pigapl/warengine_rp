package com.pigapl.warengine.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The event-wide switches, nested because {@link ClientboundAdminSnapshotPayload} hit
 * StreamCodec.composite's 6-pair ceiling. New event toggles go here, not on the snapshot itself.
 */
public record AdminEventFlags(boolean warActive, boolean keepInBase) {

    public static final StreamCodec<ByteBuf, AdminEventFlags> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, AdminEventFlags::warActive,
            ByteBufCodecs.BOOL, AdminEventFlags::keepInBase,
            AdminEventFlags::new
    );
}
