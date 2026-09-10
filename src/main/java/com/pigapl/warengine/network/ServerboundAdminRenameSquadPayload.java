package com.pigapl.warengine.network;

import com.pigapl.warengine.WarEngine;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ServerboundAdminRenameSquadPayload(String squadId, String name) implements CustomPacketPayload {

    public static final Type<ServerboundAdminRenameSquadPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WarEngine.MODID, "admin_rename_squad"));

    public static final StreamCodec<ByteBuf, ServerboundAdminRenameSquadPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ServerboundAdminRenameSquadPayload::squadId,
            ByteBufCodecs.STRING_UTF8, ServerboundAdminRenameSquadPayload::name,
            ServerboundAdminRenameSquadPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
