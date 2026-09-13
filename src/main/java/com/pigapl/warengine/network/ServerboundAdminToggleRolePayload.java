package com.pigapl.warengine.network;

import com.pigapl.warengine.WarEngine;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Toggles one of the two team roles for a player. {@code role} is {@link #COMMANDER} or
 * {@link #LEADER}; anything else is ignored server-side. Toggles rather than sets - the panel's
 * button already shows the current state.
 */
public record ServerboundAdminToggleRolePayload(UUID target, String role) implements CustomPacketPayload {

    public static final String COMMANDER = "cmd";
    public static final String LEADER = "ldr";

    public static final Type<ServerboundAdminToggleRolePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WarEngine.MODID, "admin_toggle_role"));

    public static final StreamCodec<ByteBuf, ServerboundAdminToggleRolePayload> STREAM_CODEC =
            StreamCodec.composite(
                    UUIDUtil.STREAM_CODEC, ServerboundAdminToggleRolePayload::target,
                    ByteBufCodecs.STRING_UTF8, ServerboundAdminToggleRolePayload::role,
                    ServerboundAdminToggleRolePayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
