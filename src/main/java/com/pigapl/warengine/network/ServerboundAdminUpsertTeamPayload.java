package com.pigapl.warengine.network;

import com.pigapl.warengine.WarEngine;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Admin panel's Create Team AND Change Color actions - both are one call to
 * {@code TeamKits#addTeam}, which already creates-or-adopts a team and (re)sets its color in one
 * step, so this payload does double duty rather than needing two.
 *
 * @param color a {@code ChatFormatting} color name, or {@code ""} to leave the color untouched
 */
public record ServerboundAdminUpsertTeamPayload(String id, String color) implements CustomPacketPayload {

    public static final Type<ServerboundAdminUpsertTeamPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WarEngine.MODID, "admin_upsert_team"));

    public static final StreamCodec<ByteBuf, ServerboundAdminUpsertTeamPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ServerboundAdminUpsertTeamPayload::id,
            ByteBufCodecs.STRING_UTF8, ServerboundAdminUpsertTeamPayload::color,
            ServerboundAdminUpsertTeamPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
