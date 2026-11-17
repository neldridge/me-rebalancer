package dev.functionalae.rebalancer.core;

import java.util.*;
import java.util.function.Predicate;

/** Single-server-thread transfer engine. Storage methods must honor simulation and returned counts. */
public final class RebalanceEngine<K> {
    public interface Storage<K> {
        /** Identity of the actual mounted storage, not an adapter instance. */
        Object identity();
        int priority();
        boolean available();
        Iterator<K> keys();
        boolean preferred(K key);
        long extract(K key, long amount, boolean simulate);
        long insert(K key, long amount, boolean simulate);
    }
    public interface Power {
        /** Pays for up to the requested count and returns how many items are funded. */
        long pay(long items);
    }
    public record Pending<K>(K key, long amount) {
        public Pending {
            Objects.requireNonNull(key);
            if (amount <= 0) throw new IllegalArgumentException("Empty pending transfer");
        }
    }
    public record Result(long moved, int checked, boolean passFinished, boolean settled, boolean buffered) {}

    private List<Storage<K>> topology = List.of();
    private int sourceIndex;
    private Iterator<K> keys;
    private boolean passMoved;
    private boolean passOpportunity;
    private Pending<K> pending;
    private final Runnable changed;

    public RebalanceEngine(Runnable changed) { this.changed = changed; }
    public Pending<K> pending() { return pending; }
    public void restore(Pending<K> value) { pending = value; resetScan(); }
    public void clearPending() { pending = null; changed.run(); }
    public void resetScan() { sourceIndex = 0; keys = null; passMoved = false; passOpportunity = false; }

    public Result tick(List<Storage<K>> mounts, Predicate<K> filter, long itemLimit,
                       int checkLimit, Power power) {
        if (itemLimit <= 0 || checkLimit <= 0) throw new IllegalArgumentException("Invalid work limits");
        var sorted = new ArrayList<>(mounts);
        sorted.sort(Comparator.comparingInt(Storage<K>::priority));
        // A duplicated/aliased mount is unsafe: its priority would be ambiguous.
        var seen = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
        for (var mount : sorted) {
            if (!seen.add(mount.identity())) throw new IllegalArgumentException("Duplicate storage mount");
        }
        if (!sameTopology(sorted)) { topology = sorted; resetScan(); }
        else topology = sorted; // Replace wrappers so current validity checks are used.

        // Recover previous short insertion before allowing any new extraction. Recovery may
        // return items to lower/equal priority storage; ordinary migration never does.
        if (pending != null) {
            recover(itemLimit);
            return new Result(0, 0, false, false, pending != null);
        }
        long moved = 0;
        int checked = 0;
        while (sourceIndex < topology.size() && checked < checkLimit && moved < itemLimit) {
            var source = topology.get(sourceIndex);
            if (!source.available()) { nextSource(); checked++; continue; }
            if (keys == null) keys = source.keys();
            if (!keys.hasNext()) { nextSource(); checked++; continue; }
            K key = keys.next();
            checked++;
            if (!filter.test(key)) continue;
            for (var destination : destinations(key, source.priority())) {
                if (!source.available() || !destination.available()) continue;
                long request = itemLimit - moved;
                long capacity = count(destination.insert(key, request, true), request);
                if (capacity == 0) continue;
                long extractable = count(source.extract(key, capacity, true), capacity);
                if (extractable == 0) break;
                passOpportunity = true;
                long funded = count(power.pay(extractable), extractable);
                if (funded == 0) break;
                // Power extraction may trigger grid changes. Revalidate before touching items.
                if (!source.available() || !destination.available()) break;
                long extracted = count(source.extract(key, funded, false), funded);
                if (extracted == 0) continue;
                setPending(key, extracted);
                long inserted = 0;
                if (destination.available()) {
                    inserted = count(destination.insert(key, extracted, false), extracted);
                    reducePending(inserted);
                }
                moved += inserted;
                passMoved |= inserted > 0;
                if (pending != null && source.available()) {
                    long returned = count(source.insert(key, pending.amount(), false), pending.amount());
                    reducePending(returned);
                }
                if (pending != null) return new Result(moved, checked, false, false, true);
                if (moved >= itemLimit) break;
            }
        }
        boolean finished = sourceIndex >= topology.size();
        boolean settled = finished && !passMoved && !passOpportunity;
        if (finished) resetScan();
        return new Result(moved, checked, finished, settled, false);
    }

    private boolean sameTopology(List<Storage<K>> mounts) {
        if (mounts.size() != topology.size()) return false;
        for (int i = 0; i < mounts.size(); i++) {
            var a = mounts.get(i); var b = topology.get(i);
            if (a.identity() != b.identity() || a.priority() != b.priority()) return false;
        }
        return true;
    }
    private void nextSource() { sourceIndex++; keys = null; }
    private List<Storage<K>> destinations(K key, int sourcePriority) {
        var targets = new ArrayList<Storage<K>>();
        for (var storage : topology) {
            if (storage.priority() > sourcePriority && storage.available()) targets.add(storage);
        }
        // Match AE2: priority first, preferred storage first within each priority group.
        targets.sort(Comparator.comparingInt(Storage<K>::priority).reversed()
                .thenComparing(s -> !s.preferred(key)));
        return targets;
    }
    private void recover(long limit) {
        long remainingBudget = limit;
        for (int i = topology.size() - 1; i >= 0 && pending != null && remainingBudget > 0; i--) {
            var target = topology.get(i);
            if (!target.available()) continue;
            long request = Math.min(remainingBudget, pending.amount());
            long accepted = count(target.insert(pending.key(), request, false), request);
            reducePending(accepted);
            remainingBudget -= accepted;
        }
    }
    private void setPending(K key, long amount) { pending = new Pending<>(key, amount); changed.run(); }
    private void reducePending(long count) {
        if (count == 0) return;
        pending = count == pending.amount() ? null : new Pending<>(pending.key(), pending.amount() - count);
        changed.run();
    }
    private static long count(long result, long requested) {
        if (result < 0 || result > requested) throw new IllegalStateException("Storage violated transfer contract");
        return result;
    }
}
