package com.pigapl.warengine.network;

import com.pigapl.warengine.network.client.ClientPayloadHandlers;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * The capture-point HUD feed. Its own registrar call, same version string as the other modules -
 * NeoForge allows several listeners under one shared version. NOT op-gated, but it carries no
 * coordinates, so it tells a modified client nothing it could not read off its own HUD.
 */
public final class RoundNetworking {

    private RoundNetworking() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        // Clientbound payloads must be registered on BOTH dists even on a dedicated server, or the
        // channel sets mismatch and clients are refused. The dist guard goes inside the handler body,
        // never around the registration.
        registrar.playToClient(ClientboundCapturePointsPayload.TYPE,
                ClientboundCapturePointsPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (FMLEnvironment.dist.isClient()) {
                        context.enqueueWork(() -> ClientPayloadHandlers.handleCapturePoints(payload, context));
                    }
                });
    }
}
