package dev.functionalae.rebalancer.integration;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import dev.functionalae.rebalancer.core.RebalanceEngine;
import dev.functionalae.rebalancer.mixin.NetworkStorageAccessor;
import java.util.*;
import java.util.function.Function;

/** All direct inventory access runs under AE2's own recursion and mount-change guard. */
public final class Ae2StorageAccess {
    /** Force Mixin application during startup, before any world inventory can be changed. */
    public static void verifyBridge() {
        Boolean valid = withMounts(new appeng.me.storage.NetworkStorage(), IActionSource.empty(), 2,
                mounts -> mounts.isEmpty());
        var delegate = new appeng.me.storage.NetworkStorage();
        var wrapper = new appeng.me.storage.DelegatingMEInventory(delegate);
        if (!(wrapper instanceof dev.functionalae.rebalancer.mixin.DelegatingStorageAccessor bridge)
                || bridge.rebalancer$getDelegate() != delegate)
            throw new IllegalStateException("AE2 destination-label bridge self-check failed");
        if (!Boolean.TRUE.equals(valid)) throw new IllegalStateException("AE2 storage bridge self-check failed");
    }
    public static <T> T withMounts(MEStorage network, IActionSource source, int maxMounts,
                                  Function<List<RebalanceEngine.Storage<AEItemKey>>, T> work) {
        if (!(network instanceof NetworkStorageAccessor access))
            throw new IllegalStateException("Unsupported AE2 network inventory; expected version-pinned Mixin");
        if (access.rebalancer$isBusy()) return null;
        var mounts = new ArrayList<RebalanceEngine.Storage<AEItemKey>>();
        access.rebalancer$setBusy(true);
        try {
            for (var entry : access.rebalancer$getMounts().entrySet()) {
                for (var inventory : entry.getValue()) {
                    if (mounts.size() >= maxMounts) throw new IllegalStateException("Mounted inventory limit exceeded");
                    mounts.add(new Mounted(inventory, entry.getKey(), source, access));
                }
            }
            return work.apply(mounts);
        } finally {
            access.rebalancer$setBusy(false);
            access.rebalancer$flushMountChanges();
        }
    }
    public static net.minecraft.network.chat.Component describe(RebalanceEngine.Storage<AEItemKey> storage,
                                                                DestinationLabels labels) {
        if (storage instanceof Mounted mounted) return labels.describe(mounted.inventory(), mounted.priority());
        return net.minecraft.network.chat.Component.translatable("preview.me_rebalancer.unknown");
    }
    private record Mounted(MEStorage inventory, int priority, IActionSource source,
                           NetworkStorageAccessor network) implements RebalanceEngine.Storage<AEItemKey> {
        @Override public Object identity() { return inventory; }
        @Override public boolean available() {
            return !network.rebalancer$isQueuedForRemoval(inventory);
        }
        @Override public Iterator<AEItemKey> keys() {
            // Snapshot because extracting a type can remove its live counter entry.
            var items = new ArrayList<AEItemKey>();
            for (var entry : inventory.getAvailableStacks()) {
                if (entry.getLongValue() > 0 && entry.getKey() instanceof AEItemKey item) items.add(item);
            }
            return items.iterator();
        }
        @Override public boolean preferred(AEItemKey key) { return inventory.isPreferredStorageFor(key, source); }
        @Override public long extract(AEItemKey key, long amount, boolean simulate) {
            return available() ? inventory.extract(key, amount, action(simulate), source) : 0;
        }
        @Override public long insert(AEItemKey key, long amount, boolean simulate) {
            return available() ? inventory.insert(key, amount, action(simulate), source) : 0;
        }
        private static Actionable action(boolean simulate) { return simulate ? Actionable.SIMULATE : Actionable.MODULATE; }
    }
    private Ae2StorageAccess() {}
}
