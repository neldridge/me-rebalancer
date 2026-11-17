package dev.functionalae.rebalancer;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.*;
import appeng.api.stacks.AEItemKey;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import dev.functionalae.rebalancer.core.RebalanceEngine;
import dev.functionalae.rebalancer.core.ItemSelection;
import dev.functionalae.rebalancer.integration.Ae2StorageAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import java.util.*;

public final class RebalancerBlockEntity extends AENetworkedBlockEntity implements IGridTickable {
    private final RebalanceEngine<AEItemKey> engine = new RebalanceEngine<>(this::onTransferChanged);
    private final ItemSelection<AEItemKey> selection = new ItemSelection<>();
    private long mutationVersion;
    private boolean running;
    private boolean continuous;
    private boolean faulted;
    private int cooldown;
    private long moved;
    private String status = "paused";

    public RebalancerBlockEntity(BlockPos pos, BlockState state) { this(RebalancerMod.ENTITY.get(), pos, state); }
    private RebalancerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        getMainNode().setFlags(GridFlags.REQUIRE_CHANNEL).setIdlePowerUsage(2).addService(IGridTickable.class, this);
    }
    @Override public TickingRequest getTickingRequest(IGridNode node) { return new TickingRequest(1, 20, false); }
    @Override public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (faulted || !getMainNode().isOnline()) return TickRateModulation.IDLE;
        if (cooldown > 0) { cooldown = Math.max(0, cooldown - ticksSinceLastCall); return TickRateModulation.IDLE; }
        if (!running && engine.pending() == null) return TickRateModulation.IDLE;
        var service = node.getGrid().getStorageService();
        long before = mutationVersion;
        try {
            var result = Ae2StorageAccess.withMounts(service.getInventory(), IActionSource.ofMachine(this),
                    RebalancerConfig.MAX_MOUNTS.get(), mounts -> engine.tick(mounts,
                            selection::matches, RebalancerConfig.ITEMS_PER_TICK.get(),
                            RebalancerConfig.CHECKS_PER_TICK.get(), this::pay));
            if (result == null) return TickRateModulation.IDLE;
            if (result.moved() > 0) { moved = Math.addExact(moved, result.moved()); saveChanges(); }
            status = result.buffered() ? "recovering" : "running";
            if (result.settled()) {
                status = "settled";
                if (continuous) cooldown = RebalancerConfig.IDLE_TICKS.get();
                else running = false;
                saveChanges();
            }
            return result.settled() || result.buffered() ? TickRateModulation.IDLE : TickRateModulation.URGENT;
        } catch (RuntimeException exception) {
            faulted = true; running = false; status = "faulted"; saveChanges();
            RebalancerMod.LOGGER.error("ME Rebalancer stopped at {}. Retained buffer requires inspection.", worldPosition, exception);
            return TickRateModulation.IDLE;
        } finally {
            // Direct per-mount operations bypass the combined inventory; invalidate its view.
            if (mutationVersion != before || faulted) service.invalidateCache();
        }
    }
    private void onTransferChanged() { mutationVersion++; saveChanges(); }
    private long pay(long requested) {
        var grid = getMainNode().getGrid();
        if (grid == null) return 0;
        double price = RebalancerConfig.AE_PER_ITEM.get();
        double received = grid.getEnergyService().extractAEPower(requested * price, Actionable.MODULATE, PowerMultiplier.CONFIG);
        return Math.min(requested, Math.max(0, (long) Math.floor(received / price + 1.0e-7)));
    }
    public ItemStack filterStack(int index) {
        var key = selection.get(index);
        return key == null ? ItemStack.EMPTY : key.toStack();
    }
    public void setFilter(int index, ItemStack stack) {
        if (faulted) return;
        selection.set(index, stack.isEmpty() ? null : AEItemKey.of(stack));
        selection.setAllItems(false);
        selectionChanged();
    }
    public void addFilter(ItemStack stack) {
        if (faulted || stack.isEmpty()) return;
        if (selection.add(AEItemKey.of(stack))) {
            selection.setAllItems(false);
            selectionChanged();
        }
    }
    private void selectionChanged() {
        selectionRevision++;
        cooldown = 0; engine.resetScan(); status = running ? "running" : "paused"; saveChanges();
    }
    public void menuAction(int action) {
        if (faulted) return;
        switch (action) {
            case 0 -> {
                running = !running && selection.canRun();
                cooldown = 0; engine.resetScan(); status = running ? "running" : "paused"; saveChanges();
            }
            case 1 -> { continuous = !continuous; saveChanges(); }
            case 2 -> { selection.setAllItems(!selection.allItems()); selectionChanged(); }
            case 3 -> { selection.clear(); selectionChanged(); }
            default -> { }
        }
    }
    private int selectionRevision;
    public int selectionRevision() { return selectionRevision; }
    public boolean selected(AEItemKey key) { return selection.matches(key); }
    public void pauseForPreview() {
        running = false; cooldown = 0; engine.resetScan(); status = "paused"; saveChanges();
    }
    public boolean running() { return running; }
    public boolean continuous() { return continuous; }
    public boolean allItems() { return selection.allItems(); }
    public boolean faulted() { return faulted; }
    public boolean canStart() { return selection.canRun() && !faulted; }
    public long moved() { return moved; }
    public long buffered() { return engine.pending() == null ? 0 : engine.pending().amount(); }
    public int statusCode() {
        if (faulted) return 4;
        if (!getMainNode().isOnline()) return 5;
        if (engine.pending() != null) return 3;
        if ("settled".equals(status)) return 2;
        return running ? 1 : 0;
    }
    @Override public void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putBoolean("running", running); output.putBoolean("continuous", continuous);
        output.putBoolean("faulted", faulted); output.putLong("moved", moved);
        output.putBoolean("allItems", selection.allItems());
        var slots = output.childrenList("filterSlots");
        for (int i = 0; i < ItemSelection.SIZE; i++) {
            var key = selection.get(i);
            if (key != null) {
                var entry = slots.addChild();
                entry.putInt("slot", i);
                entry.store("key", AEItemKey.CODEC, key);
            }
        }
        var pending = engine.pending();
        if (pending != null) {
            output.store("pendingKey", AEItemKey.CODEC, pending.key()); output.putLong("pendingAmount", pending.amount());
        }
    }
    @Override public void loadTag(ValueInput input) {
        super.loadTag(input);
        running = input.getBooleanOr("running", false); continuous = input.getBooleanOr("continuous", false);
        faulted = input.getBooleanOr("faulted", false); moved = input.getLongOr("moved", 0);
        selection.clear();
        if (input.childrenList("filterSlots").isPresent()) {
            for (var entry : input.childrenListOrEmpty("filterSlots")) {
                int slot = entry.getIntOr("slot", -1);
                var filter = entry.read("key", AEItemKey.CODEC).orElse(null);
                if (slot >= 0 && slot < ItemSelection.SIZE && filter != null) selection.set(slot, filter);
            }
        } else {
            // Import the original prototype's filters. Its implicit all-items mode is not retained.
            for (var filter : input.read("filters", AEItemKey.CODEC.listOf()).orElse(List.of())) selection.add(filter);
        }
        selection.setAllItems(input.getBooleanOr("allItems", false));
        if (!selection.canRun()) running = false;
        var key = input.read("pendingKey", AEItemKey.CODEC).orElse(null);
        long amount = input.getLongOr("pendingAmount", 0);
        engine.restore(key != null && amount > 0 ? new RebalanceEngine.Pending<>(key, amount) : null);
        if (amount > 0 && key == null) faulted = true;
        status = faulted ? "faulted" : running ? "running" : "paused";
    }
    @Override public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        var pending = engine.pending();
        if (pending != null) pending.key().addDrops(pending.amount(), drops, level, pos);
    }
    @Override public void clearContent() { super.clearContent(); engine.clearPending(); }
}
