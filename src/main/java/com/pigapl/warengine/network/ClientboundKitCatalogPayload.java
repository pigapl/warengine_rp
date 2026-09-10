package com.pigapl.warengine.network;

import com.pigapl.warengine.WarEngine;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record ClientboundKitCatalogPayload(List<KitCatalogEntry> kits) implements CustomPacketPayload {

    public static final Type<ClientboundKitCatalogPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(WarEngine.MODID, "kit_catalog"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundKitCatalogPayload> STREAM_CODEC =
            KitCatalogEntry.STREAM_CODEC.apply(ByteBufCodecs.list())
                    .map(ClientboundKitCatalogPayload::new, ClientboundKitCatalogPayload::kits);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
