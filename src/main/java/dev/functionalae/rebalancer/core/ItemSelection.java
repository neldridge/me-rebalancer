package dev.functionalae.rebalancer.core;

import java.util.*;

/** All items by default; explicit whitelist and blacklist use exact item keys. */
public final class ItemSelection<K> {
    public static final int SIZE = 64;
    private final List<K> slots = new ArrayList<>(Collections.nCopies(SIZE, null));
    public enum Mode { ALL, WHITELIST, BLACKLIST }
    private Mode mode = Mode.ALL;

    public K get(int slot) { return slots.get(slot); }
    public boolean allItems() { return mode == Mode.ALL; }
    public Mode mode() { return mode; }
    public void setMode(Mode value) { mode = Objects.requireNonNull(value); }
    public void nextMode() { mode = Mode.values()[(mode.ordinal() + 1) % Mode.values().length]; }
    public boolean matches(K key) { Objects.requireNonNull(key);
        return switch (mode) {
            case ALL -> true;
            case WHITELIST -> slots.contains(key);
            case BLACKLIST -> !slots.contains(key);
        }; }
    public boolean canRun() { return mode != Mode.WHITELIST || count() > 0; }
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
    public void clear() { Collections.fill(slots, null); }
}
