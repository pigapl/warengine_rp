package com.pigapl.warengine.network;

import com.pigapl.warengine.WarEngine;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Grows or shrinks a team's base by {@code delta} blocks. A delta rather than an absolute size, so the
 * panel never needs to know the current radius and two quick clicks cannot race each other.
 */
public record ServerboundAdminAdjustBaseRadiusPayload(String team, int delta) implements CustomPacketPayload {

    public static final Type<ServerboundAdminAdjustBaseRadiusPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WarEngine.MODID, "admin_adjust_base_radius"));

    public static final StreamCodec<ByteBuf, ServerboundAdminAdjustBaseRadiusPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, ServerboundAdminAdjustBaseRadiusPayload::team,
                    ByteBufCodecs.VAR_INT, ServerboundAdminAdjustBaseRadiusPayload::delta,
                    ServerboundAdminAdjustBaseRadiusPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
