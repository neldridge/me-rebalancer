package dev.functionalae.rebalancer.mixin;

import appeng.api.storage.MEStorage;
import appeng.me.storage.NetworkStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import java.util.List;
import java.util.NavigableMap;

/** Version-pinned bridge; never edits AE2's mount table. */
@Mixin(value = NetworkStorage.class, remap = false)
public interface NetworkStorageAccessor {
    @Accessor("priorityInventory")
    NavigableMap<Integer, List<MEStorage>> rebalancer$getMounts();
    @Accessor("mountsInUse")
    boolean rebalancer$isBusy();
    @Accessor("mountsInUse")
    void rebalancer$setBusy(boolean value);
    @Invoker("isQueuedForRemoval")
    boolean rebalancer$isQueuedForRemoval(MEStorage storage);
    @Invoker("flushQueuedOperations")
    void rebalancer$flushMountChanges();
}
