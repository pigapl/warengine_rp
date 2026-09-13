package com.pigapl.warengine.network;

import com.pigapl.warengine.WarEngine;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** {@code canCreate}: may this player found a squad at all - see {@code RoleService}. */
public record ClientboundSquadStatePayload(String squadId, boolean canCreate, boolean commander)
        implements CustomPacketPayload {

    public static final Type<ClientboundSquadStatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WarEngine.MODID, "squad_state"));

    public static final StreamCodec<ByteBuf, ClientboundSquadStatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ClientboundSquadStatePayload::squadId,
            ByteBufCodecs.BOOL, ClientboundSquadStatePayload::canCreate,
            ByteBufCodecs.BOOL, ClientboundSquadStatePayload::commander,
            ClientboundSquadStatePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
