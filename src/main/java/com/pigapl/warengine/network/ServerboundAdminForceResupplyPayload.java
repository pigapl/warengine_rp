package com.pigapl.warengine.network;

import com.pigapl.warengine.WarEngine;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** Admin panel's "Resupply" action - runs the same reconcile top-up {@code /kit resupply} does. */
public record ServerboundAdminForceResupplyPayload(UUID target) implements CustomPacketPayload {

    public static final Type<ServerboundAdminForceResupplyPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WarEngine.MODID, "admin_force_resupply"));

    public static final StreamCodec<ByteBuf, ServerboundAdminForceResupplyPayload> STREAM_CODEC =
            UUIDUtil.STREAM_CODEC.map(ServerboundAdminForceResupplyPayload::new,
                    ServerboundAdminForceResupplyPayload::target);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
