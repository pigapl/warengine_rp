package com.pigapl.warengine.network.client;

import com.pigapl.warengine.network.AdminBudgetRow;
import com.pigapl.warengine.network.AdminKitInfo;
import com.pigapl.warengine.network.ClientboundAdminBudgetSnapshotPayload;
import com.pigapl.warengine.network.ClientboundAdminKitsSnapshotPayload;
import com.pigapl.warengine.network.ClientboundAdminScarceSnapshotPayload;
import com.pigapl.warengine.network.ClientboundAdminSnapshotPayload;
import com.pigapl.warengine.network.ClientboundAdminTeamsSnapshotPayload;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * The latest server-pushed admin snapshots, plus a one-shot "open the admin screen" flag.
 * {@link #consumeOpenRequest()} clears itself on read, or a sticky flag would force the screen back
 * open every time the player closed it.
 *
 * <p><b>Every setter skips the bump when nothing actually changed.</b> The screens POLL once a
 * second, so without this every reply rebuilt every widget - which wiped an {@code EditBox} mid-type.
 * Kit/scarce snapshots carry an {@link ItemStack}, which has no meaningful {@code equals()} here, so
 * they compare field-by-field with {@code ItemStack.matches}.</p>
 */
public final class ClientAdminCache {

    private static volatile ClientboundAdminSnapshotPayload snapshot = null;
    private static volatile ClientboundAdminTeamsSnapshotPayload teamsSnapshot = null;
    private static volatile ClientboundAdminKitsSnapshotPayload kitsSnapshot = null;
    private static volatile ClientboundAdminScarceSnapshotPayload scarceSnapshot = null;
    private static volatile ClientboundAdminBudgetSnapshotPayload budgetSnapshot = null;
    private static volatile int revision = 0;
    private static volatile boolean openRequested = false;

    private ClientAdminCache() {}

    public static ClientboundAdminSnapshotPayload snapshot() {
        return snapshot;
    }

    public static ClientboundAdminTeamsSnapshotPayload teamsSnapshot() {
        return teamsSnapshot;
    }

    public static ClientboundAdminKitsSnapshotPayload kitsSnapshot() {
        return kitsSnapshot;
    }

    public static ClientboundAdminScarceSnapshotPayload scarceSnapshot() {
        return scarceSnapshot;
    }

    public static ClientboundAdminBudgetSnapshotPayload budgetSnapshot() {
        return budgetSnapshot;
    }

    public static int revision() {
        return revision;
    }

    public static boolean consumeOpenRequest() {
        if (!openRequested) {
            return false;
        }
        openRequested = false;
        return true;
    }

    /** Still bumps ~1/s during a war ({@code timeLeftMillis} moves); the win is the idle case. */
    static void setSnapshot(ClientboundAdminSnapshotPayload value) {
        if (value.equals(snapshot)) {
            return;
        }
        snapshot = value;
        revision++;
    }

    static void setTeamsSnapshot(ClientboundAdminTeamsSnapshotPayload value) {
        if (value.equals(teamsSnapshot)) {
            return;
        }
        teamsSnapshot = value;
        revision++;
    }

    static void setKitsSnapshot(ClientboundAdminKitsSnapshotPayload value) {
        if (kitsSnapshot != null && kitsEqual(value.kits(), kitsSnapshot.kits())
                && value.teamIds().equals(kitsSnapshot.teamIds())) {
            return;
        }
        kitsSnapshot = value;
        revision++;
    }

    static void setScarceSnapshot(ClientboundAdminScarceSnapshotPayload value) {
        if (scarceSnapshot != null && itemsEqual(value.items(), scarceSnapshot.items())) {
            return;
        }
        scarceSnapshot = value;
        revision++;
    }

    static void setBudgetSnapshot(ClientboundAdminBudgetSnapshotPayload value) {
        if (budgetSnapshot != null && budgetRowsEqual(value.rows(), budgetSnapshot.rows())) {
            return;
        }
        budgetSnapshot = value;
        revision++;
    }

    static void requestOpen() {
        openRequested = true;
    }

    private static boolean budgetRowsEqual(List<AdminBudgetRow> a, List<AdminBudgetRow> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            AdminBudgetRow x = a.get(i);
            AdminBudgetRow y = b.get(i);
            if (!x.team().equals(y.team()) || !x.kitId().equals(y.kitId())
                    || !x.displayName().equals(y.displayName())
                    || x.total() != y.total() || x.reserved() != y.reserved()
                    || !ItemStack.matches(x.icon(), y.icon())) {
                return false;
            }
        }
        return true;
    }

    private static boolean kitsEqual(List<AdminKitInfo> a, List<AdminKitInfo> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            AdminKitInfo x = a.get(i);
            AdminKitInfo y = b.get(i);
            if (!x.id().equals(y.id()) || !x.displayName().equals(y.displayName())
                    || x.limit() != y.limit() || !x.assignedTeams().equals(y.assignedTeams())
                    || !ItemStack.matches(x.icon(), y.icon())) {
                return false;
            }
        }
        return true;
    }

    private static boolean itemsEqual(List<ItemStack> a, List<ItemStack> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!ItemStack.matches(a.get(i), b.get(i))) {
                return false;
            }
        }
        return true;
    }
}
