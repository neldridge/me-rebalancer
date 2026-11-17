package dev.functionalae.rebalancer.menu;

import dev.functionalae.rebalancer.*;
import dev.functionalae.rebalancer.core.ItemSelection;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.network.chat.Component;
import appeng.api.stacks.AEItemKey;
import appeng.api.networking.security.IActionSource;
import dev.functionalae.rebalancer.core.PreviewPlanner;
import dev.functionalae.rebalancer.integration.Ae2StorageAccess;
import dev.functionalae.rebalancer.integration.DestinationLabels;
import java.util.*;

/** Only server-owned ghost filters are editable; no real item ever enters the ghost container. */
public final class RebalancerMenu extends AbstractContainerMenu {
    public static final int PAGE_SIZE = 18;
    public static final int PAGES = (ItemSelection.SIZE + PAGE_SIZE - 1) / PAGE_SIZE;
    private final RebalancerBlockEntity entity;
    private final SimpleContainer filters = new SimpleContainer(ItemSelection.SIZE);
    private final ContainerData data;
    private int page;
    public static final int PREVIEW_ROWS = 5;
    public static final int PREVIEW_SLOT_START = ItemSelection.SIZE + 36;
    private final SimpleContainer previewIcons = new SimpleContainer(PREVIEW_ROWS);
    private boolean previewView;
    private PreviewPlanner<AEItemKey> preview;
    private DestinationLabels destinationLabels;
    private List<ItemStack> previewResults = List.of();
    private int previewState, previewPage, previewRevision;
    private long lastPreviewTick = -1, previewStarted, lastPreviewRequest = -1000;

    public RebalancerMenu(int id, Inventory inventory) { this(id, inventory, null); }
    public RebalancerMenu(int id, Inventory inventory, RebalancerBlockEntity entity) {
        super(RebalancerMod.MENU.get(), id);
        this.entity = entity;
        data = entity == null ? new SimpleContainerData(14) : new ContainerData() {
            public int get(int index) {
                if (index == 0) return (entity.running() ? 1 : 0) | (entity.continuous() ? 2 : 0)
                        | (entity.allItems() ? 4 : 0) | (entity.canStart() ? 8 : 0) | (entity.faulted() ? 16 : 0) | (entity.blacklist() ? 32 : 0);
                if (index == 1) return entity.statusCode();
                if (index == 10) return previewState;
                if (index == 11) return previewPage;
                if (index == 12) return Math.max(1, (previewResults.size() + PREVIEW_ROWS - 1) / PREVIEW_ROWS);
                if (index == 13) return previewResults.size();
                long value = index < 6 ? entity.moved() : entity.buffered();
                return (int) (value >>> ((index < 6 ? index - 2 : index - 6) * 16)) & 0xffff;
            }
            public void set(int index, int value) { }
            public int getCount() { return 14; }
        };
        addDataSlots(data);
        for (int i = 0; i < ItemSelection.SIZE; i++) {
            final int filterIndex = i;
            int cell = i % PAGE_SIZE;
            addSlot(new Slot(filters, i, 43 + cell % 9 * 18, 60 + cell / 9 * 18) {
                @Override public boolean mayPlace(ItemStack stack) { return false; }
                @Override public boolean mayPickup(Player player) { return false; }
                @Override public boolean isActive() { return !previewView && filterIndex / PAGE_SIZE == page; }
            });
        }
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addPlayerSlot(inventory, 9 + row * 9 + col, 43 + col * 18, 150 + row * 18);
        for (int col = 0; col < 9; col++) addPlayerSlot(inventory, col, 43 + col * 18, 208);
        for (int i = 0; i < PREVIEW_ROWS; i++) addSlot(new Slot(previewIcons, i, 12, 65 + i * 28) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
            @Override public boolean mayPickup(Player player) { return false; }
            @Override public boolean isActive() { return previewView; }
        });
        refreshFilters();
    }
    private void addPlayerSlot(Inventory inventory, int index, int x, int y) {
        addSlot(new Slot(inventory, index, x, y) {
            @Override public boolean isActive() { return !previewView; }
        });
    }
    private void refreshFilters() {
        if (entity != null) for (int i = 0; i < ItemSelection.SIZE; i++) filters.setItem(i, entity.filterStack(i));
    }
    @Override public void broadcastChanges() {
        advancePreview(); refreshFilters();
        if (entity != null) for (int i = 0; i < PREVIEW_ROWS; i++) {
            int index = previewPage * PREVIEW_ROWS + i;
            previewIcons.setItem(i, index < previewResults.size() ? previewResults.get(index).copy() : ItemStack.EMPTY);
        }
        super.broadcastChanges();
    }
    private void beginPreview() {
        long now = entity.getLevel().getGameTime();
        if (now - lastPreviewRequest < 10) return;
        lastPreviewRequest = now;
        preview = null; previewResults = List.of(); previewPage = 0;
        previewRevision = entity.selectionRevision();
        entity.pauseForPreview();
        var grid = entity.getMainNode().getGrid();
        if (!entity.canStart() || !entity.getMainNode().isOnline() || entity.buffered() != 0 || grid == null) {
            previewState = 4; return;
        }
        try { destinationLabels = new DestinationLabels(grid); }
        catch (RuntimeException exception) {
            previewState = 5;
            RebalancerMod.LOGGER.warn("ME Rebalancer destination lookup failed", exception);
            return;
        }
        preview = new PreviewPlanner<>(entity::selected, 256, 4096);
        previewState = 1; previewStarted = now; lastPreviewTick = -1;
    }
    private void advancePreview() {
        if (entity == null || previewState == 0) return;
        if (entity.selectionRevision() != previewRevision || entity.running()) {
            preview = null; previewResults = List.of(); previewState = 6; previewPage = 0; return;
        }
        if (preview == null) return;
        long now = entity.getLevel().getGameTime();
        if (lastPreviewTick == now) return;
        lastPreviewTick = now;
        var grid = entity.getMainNode().getGrid();
        if (grid == null || !entity.getMainNode().isOnline() || now - previewStarted > 1200) {
            preview = null; previewState = 5; return;
        }
        try {
            Ae2StorageAccess.withMounts(grid.getStorageService().getInventory(), IActionSource.ofMachine(entity),
                    RebalancerConfig.MAX_MOUNTS.get(), mounts -> { preview.step(mounts, 8); return true; });
            if (preview.done()) {
                var rows = new ArrayList<ItemStack>();
                var results = new ArrayList<>(preview.rows());
                results.sort(Comparator.comparingLong(PreviewPlanner.Row<AEItemKey>::amount).reversed());
                for (var row : results) {
                    var icon = row.key().toStack();
                    var destination = Ae2StorageAccess.describe(row.destination(), destinationLabels);
                    icon.set(DataComponents.LORE, new ItemLore(List.of(
                            Component.translatable("preview.me_rebalancer.amount", String.format(Locale.ROOT, "%,d", row.amount())),
                            destination, Component.translatable("preview.me_rebalancer.estimate_help"))));
                    rows.add(icon);
                }
                previewResults = List.copyOf(rows);
                previewState = preview.limited() ? 3 : 2;
                preview = null;
            }
        } catch (RuntimeException exception) {
            preview = null; previewResults = List.of(); previewState = 5;
            RebalancerMod.LOGGER.warn("ME Rebalancer preview stopped at {}", entity.getBlockPos(), exception);
        }
    }
    @Override public boolean stillValid(Player player) {
        return entity == null || (!entity.isRemoved() && entity.getLevel() == player.level()
                && player.distanceToSqr(entity.getBlockPos().getX() + .5, entity.getBlockPos().getY() + .5,
                entity.getBlockPos().getZ() + .5) <= 64);
    }
    @Override public boolean clickMenuButton(Player player, int action) {
        if (entity == null || !stillValid(player) || action < 0 || action > 7) return false;
        if (action <= 3) entity.menuAction(action);
        else if (action == 4) beginPreview();
        else if (action == 5) { preview = null; previewResults = List.of(); previewState = 0; }
        else if (action == 6) previewPage = Math.max(0, previewPage - 1);
        else if (action == 7) previewPage = Math.min(Math.max(0, (previewResults.size() - 1) / PREVIEW_ROWS), previewPage + 1);
        broadcastChanges(); return true;
    }
    @Override public void clicked(int slot, int button, ContainerInput type, Player player) {
        if (!stillValid(player)) return;
        if (slot >= PREVIEW_SLOT_START) return;
        if (slot >= 0 && slot < ItemSelection.SIZE) {
            if (entity != null && (type == ContainerInput.PICKUP || type == ContainerInput.QUICK_MOVE)) {
                if (button == 1 || type == ContainerInput.QUICK_MOVE) entity.setFilter(slot, ItemStack.EMPTY);
                else if (button == 0) entity.setFilter(slot, getCarried());
                broadcastChanges();
            }
            return; // Never allow clone, throw, swap or quick-craft to materialize ghost items.
        }
        super.clicked(slot, button, type, player);
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (entity != null && stillValid(player) && index >= ItemSelection.SIZE && index < PREVIEW_SLOT_START) {
            entity.addFilter(slots.get(index).getItem()); broadcastChanges();
        }
        return ItemStack.EMPTY; // Leave the actual stack exactly where it is.
    }
    @Override public boolean canDragTo(Slot slot) { return slot.index >= ItemSelection.SIZE && slot.index < PREVIEW_SLOT_START && super.canDragTo(slot); }
    @Override public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.index >= ItemSelection.SIZE && slot.index < PREVIEW_SLOT_START && super.canTakeItemForPickAll(stack, slot);
    }
    public boolean previewView() { return previewView; }
    public void setPreviewView(boolean value) { previewView = value; }
    public int previewState() { return data.get(10); }
    public int previewPage() { return data.get(11); }
    public int previewPages() { return data.get(12); }
    public int previewCount() { return data.get(13); }
    public ItemStack previewIcon(int row) { return previewIcons.getItem(row); }
    public int page() { return page; }
    public void changePage(int delta) { page = Math.clamp(page + delta, 0, PAGES - 1); }
    public boolean flag(int mask) { return (data.get(0) & mask) != 0; }
    public int status() { return data.get(1); }
    public long moved() { return readLong(2); }
    public long buffered() { return readLong(6); }
    private long readLong(int start) {
        long value = 0;
        for (int i = 0; i < 4; i++) value |= (long) (data.get(start + i) & 0xffff) << (16 * i);
        return value;
    }
}
