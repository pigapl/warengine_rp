package com.pigapl.warengine.network;

import com.pigapl.warengine.WarEngine;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** Admin panel's per-member "Kick" button. The handler MUST re-check op status - see {@code AdminNetworking}. */
public record ServerboundAdminKickSquadMemberPayload(UUID target) implements CustomPacketPayload {

    public static final Type<ServerboundAdminKickSquadMemberPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WarEngine.MODID, "admin_kick_squad_member"));

    public static final StreamCodec<ByteBuf, ServerboundAdminKickSquadMemberPayload> STREAM_CODEC =
            UUIDUtil.STREAM_CODEC.map(ServerboundAdminKickSquadMemberPayload::new,
                    ServerboundAdminKickSquadMemberPayload::target);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
