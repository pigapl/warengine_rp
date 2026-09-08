package com.pigapl.warengine.network;

import com.pigapl.warengine.WarEngine;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** Admin panel's "TP" button - teleports the ADMIN to the target player. Target must be online. */
public record ServerboundAdminTeleportToPlayerPayload(UUID target) implements CustomPacketPayload {

    public static final Type<ServerboundAdminTeleportToPlayerPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WarEngine.MODID, "admin_tp_to_player"));

    public static final StreamCodec<ByteBuf, ServerboundAdminTeleportToPlayerPayload> STREAM_CODEC =
            UUIDUtil.STREAM_CODEC.map(ServerboundAdminTeleportToPlayerPayload::new,
                    ServerboundAdminTeleportToPlayerPayload::target);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
