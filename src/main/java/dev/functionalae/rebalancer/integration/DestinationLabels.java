package dev.functionalae.rebalancer.integration;

import appeng.api.implementations.blockentities.IChestOrDrive;
import appeng.api.networking.IGrid;
import appeng.api.storage.MEStorage;
import appeng.parts.storagebus.StorageBusPart;
import dev.functionalae.rebalancer.mixin.DelegatingStorageAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.*;

public final class DestinationLabels {
    private final Map<MEStorage, Component> cells = new IdentityHashMap<>();
    private final Map<MEStorage, Component> busTargets = new IdentityHashMap<>();
    public DestinationLabels(IGrid grid) {
        for (var node : grid.getNodes()) {
            var owner = node.getOwner();
            if (owner instanceof BlockEntity block && owner instanceof IChestOrDrive drive) {
                for (int slot = 0; slot < drive.getCellCount(); slot++) {
                    var inventory = drive.getCellInventory(slot);
                    if (inventory != null) cells.put(inventory, Component.translatable("preview.me_rebalancer.cell",
                            location(block.getLevel(), block.getBlockPos()), slot + 1));
                }
            }
            if (owner instanceof StorageBusPart bus && bus.getLevel() != null && bus.getSide() != null) {
                busTargets.put(bus.getInternalHandler(), location(bus.getLevel(), bus.getBlockEntity().getBlockPos().relative(bus.getSide())));
            }
        }
    }
    public Component describe(MEStorage inventory, int priority) {
        Component label = cells.get(inventory);
        if (label == null && inventory instanceof DelegatingStorageAccessor wrapper) label = busTargets.get(wrapper.rebalancer$getDelegate());
        if (label == null) label = inventory.getDescription();
        if (label == null) label = Component.translatable("preview.me_rebalancer.unknown");
        return Component.translatable("preview.me_rebalancer.destination", label, priority);
    }
    private static Component location(Level level, BlockPos pos) {
        if (level == null || !level.hasChunkAt(pos)) return Component.translatable("preview.me_rebalancer.unknown");
        return Component.translatable("preview.me_rebalancer.location", level.getBlockState(pos).getBlock().getName(),
                pos.getX(), pos.getY(), pos.getZ(), level.dimension().identifier().toString());
    }
}
