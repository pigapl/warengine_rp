package com.pigapl.warengine.network.client;

import com.pigapl.warengine.network.ClientboundAdminKitsSnapshotPayload;
import com.pigapl.warengine.network.ClientboundAdminScarceSnapshotPayload;
import com.pigapl.warengine.network.ClientboundAdminSnapshotPayload;
import com.pigapl.warengine.network.ClientboundAdminBudgetSnapshotPayload;
import com.pigapl.warengine.network.ClientboundAdminTeamsSnapshotPayload;
import com.pigapl.warengine.network.ClientboundCapturePointsPayload;
import com.pigapl.warengine.network.ClientboundKitBudgetPayload;
import com.pigapl.warengine.network.ClientboundKitCatalogPayload;
import com.pigapl.warengine.network.ClientboundKitConfirmPayload;
import com.pigapl.warengine.network.ClientboundKitStatePayload;
import com.pigapl.warengine.network.ClientboundOpenAdminScreenPayload;
import com.pigapl.warengine.network.ClientboundSquadListPayload;
import com.pigapl.warengine.network.ClientboundSquadStatePayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ClientPayloadHandlers {
    private ClientPayloadHandlers() {}

    public static void handleKitCatalog(ClientboundKitCatalogPayload payload, IPayloadContext context) {
        ClientKitCache.setCatalog(payload.kits());
    }

    public static void handleKitState(ClientboundKitStatePayload payload, IPayloadContext context) {
        ClientKitCache.setKitId(payload.kitId());
    }

    public static void handleKitConfirm(ClientboundKitConfirmPayload payload, IPayloadContext context) {
        ClientKitCache.setPendingConfirm(new ClientKitCache.PendingConfirm(payload.kitId(), payload.lines()));
    }

    public static void handleSquadList(ClientboundSquadListPayload payload, IPayloadContext context) {
        ClientSquadCache.setSquads(payload.squads());
    }

    public static void handleSquadState(ClientboundSquadStatePayload payload, IPayloadContext context) {
        ClientSquadCache.setSquadId(payload.squadId());
    }

    public static void handleKitBudget(ClientboundKitBudgetPayload payload, IPayloadContext context) {
        ClientKitBudgetCache.set(payload.entries());
    }

    public static void handleAdminSnapshot(ClientboundAdminSnapshotPayload payload, IPayloadContext context) {
        ClientAdminCache.setSnapshot(payload);
    }

    public static void handleOpenAdminScreen(IPayloadContext context) {
        ClientAdminCache.requestOpen();
    }

    public static void handleAdminTeamsSnapshot(ClientboundAdminTeamsSnapshotPayload payload, IPayloadContext context) {
        ClientAdminCache.setTeamsSnapshot(payload);
    }

    public static void handleAdminKitsSnapshot(ClientboundAdminKitsSnapshotPayload payload, IPayloadContext context) {
        ClientAdminCache.setKitsSnapshot(payload);
    }

    public static void handleAdminScarceSnapshot(ClientboundAdminScarceSnapshotPayload payload, IPayloadContext context) {
        ClientAdminCache.setScarceSnapshot(payload);
    }

    public static void handleAdminBudgetSnapshot(ClientboundAdminBudgetSnapshotPayload payload, IPayloadContext context) {
        ClientAdminCache.setBudgetSnapshot(payload);
    }

    public static void handleCapturePoints(ClientboundCapturePointsPayload payload, IPayloadContext context) {
        ClientCapturePointCache.set(payload);
    }
}
