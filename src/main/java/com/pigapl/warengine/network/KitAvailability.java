package com.pigapl.warengine.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

/**
 * What the receiving player can get from this kit, and when. Split out of {@link KitCatalogEntry}
 * because StreamCodec.composite tops out at 6 field/getter pairs - the entry itself is at that
 * ceiling, so anything else about a kit has to arrive through here.
 *
 * <p>{@code scarceNames}: the kit's rationed items, already labelled server-side (a TACZ gun's hover
 * name on the server is the generic item key, so the client cannot name them itself).</p>
 */
public record KitAvailability(int limit, int taken, boolean offered, List<String> scarceNames) {

    public static final StreamCodec<RegistryFriendlyByteBuf, KitAvailability> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, KitAvailability::limit,
            ByteBufCodecs.VAR_INT, KitAvailability::taken,
            ByteBufCodecs.BOOL, KitAvailability::offered,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), KitAvailability::scarceNames,
            KitAvailability::new
    );

    public boolean unlimited() {
        return limit <= 0;
    }

    public boolean full() {
        return !unlimited() && taken >= limit;
    }
}
