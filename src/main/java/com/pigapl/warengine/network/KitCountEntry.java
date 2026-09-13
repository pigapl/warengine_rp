package com.pigapl.warengine.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** One kit reservation a squad holds. {@code displayName} so the screen needs no second lookup. */
public record KitCountEntry(String kitId, String displayName, int count) {

    public static final StreamCodec<ByteBuf, KitCountEntry> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, KitCountEntry::kitId,
            ByteBufCodecs.STRING_UTF8, KitCountEntry::displayName,
            ByteBufCodecs.VAR_INT, KitCountEntry::count,
            KitCountEntry::new
    );
}
