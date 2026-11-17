package dev.functionalae.rebalancer.mixin;

import appeng.api.storage.MEStorage;
import appeng.me.storage.DelegatingMEInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Matches a mounted bus wrapper to its public storage-bus owner without remounting providers. */
@Mixin(value = DelegatingMEInventory.class, remap = false)
public interface DelegatingStorageAccessor {
    @Invoker("getDelegate")
    MEStorage rebalancer$getDelegate();
}
