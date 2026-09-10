package com.pigapl.warengine.network;

import com.pigapl.warengine.WarEngine;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ServerboundAdminRestoreTeamsPayload() implements CustomPacketPayload {

    public static final Type<ServerboundAdminRestoreTeamsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WarEngine.MODID, "admin_restore_teams"));

    public static final StreamCodec<ByteBuf, ServerboundAdminRestoreTeamsPayload> STREAM_CODEC =
            StreamCodec.unit(new ServerboundAdminRestoreTeamsPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
