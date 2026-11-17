package dev.functionalae.rebalancer.core;

import java.util.*;

/** Explicit selection: an empty whitelist matches nothing, never everything. */
public final class ItemSelection<K> {
    public static final int SIZE = 64;
    private final List<K> slots = new ArrayList<>(Collections.nCopies(SIZE, null));
    private boolean allItems;

    public K get(int slot) { return slots.get(slot); }
    public boolean allItems() { return allItems; }
    public void setAllItems(boolean value) { allItems = value; }
    public boolean matches(K key) { return allItems || slots.contains(Objects.requireNonNull(key)); }
    public boolean canRun() { return allItems || count() > 0; }
    public int count() { return (int) slots.stream().filter(Objects::nonNull).count(); }
    public void set(int slot, K key) {
        if (slot < 0 || slot >= SIZE) throw new IndexOutOfBoundsException(slot);
        if (key != null) {
            for (int i = 0; i < SIZE; i++) if (i != slot && key.equals(slots.get(i))) slots.set(i, null);
        }
        slots.set(slot, key);
    }
    public boolean add(K key) {
        Objects.requireNonNull(key);
        if (slots.contains(key)) return false;
        int empty = slots.indexOf(null);
        if (empty < 0) return false;
        set(empty, key);
        return true;
    }
    public void clear() { Collections.fill(slots, null); allItems = false; }
}
