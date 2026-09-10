package com.pigapl.warengine.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Slot availability for one kit on the receiving player's team. Split out of
 * {@link KitCatalogEntry} because StreamCodec.composite tops out at 6 field/getter pairs.
 */
public record KitAvailability(int limit, int taken) {

    public static final StreamCodec<RegistryFriendlyByteBuf, KitAvailability> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, KitAvailability::limit,
            ByteBufCodecs.VAR_INT, KitAvailability::taken,
            KitAvailability::new
    );

    public boolean unlimited() {
        return limit <= 0;
    }

    public boolean full() {
        return !unlimited() && taken >= limit;
    }
}
