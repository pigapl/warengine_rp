package com.pigapl.warengine.network;

import com.pigapl.warengine.WarEngine;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Name and limit together - the manage screen saves both at once. Gated by {@code RoleService}. */
public record ServerboundSquadEditPayload(String squadId, String name, int limit)
        implements CustomPacketPayload {

    public static final Type<ServerboundSquadEditPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WarEngine.MODID, "squad_edit"));

    public static final StreamCodec<ByteBuf, ServerboundSquadEditPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ServerboundSquadEditPayload::squadId,
            ByteBufCodecs.STRING_UTF8, ServerboundSquadEditPayload::name,
            ByteBufCodecs.VAR_INT, ServerboundSquadEditPayload::limit,
            ServerboundSquadEditPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
