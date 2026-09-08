package com.pigapl.warengine.network;

import com.pigapl.warengine.WarEngine;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** Admin panel's per-member "Move to squad" action. Online players only - see {@code AdminNetworking}. */
public record ServerboundAdminMoveToSquadPayload(UUID target, String squadId) implements CustomPacketPayload {

    public static final Type<ServerboundAdminMoveToSquadPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WarEngine.MODID, "admin_move_to_squad"));

    public static final StreamCodec<ByteBuf, ServerboundAdminMoveToSquadPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, ServerboundAdminMoveToSquadPayload::target,
            ByteBufCodecs.STRING_UTF8, ServerboundAdminMoveToSquadPayload::squadId,
            ServerboundAdminMoveToSquadPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
