package dev.functionalae.rebalancer.core;

import java.util.*;
import java.util.function.Predicate;

/** Bounded, incremental, simulation-only estimate. Never calls a storage with simulate=false. */
public final class PreviewPlanner<K> {
    public record Row<K>(K key, long amount, RebalanceEngine.Storage<K> destination) {}
    private final Predicate<K> filter;
    private final int rowLimit, keyLimit;
    private List<RebalanceEngine.Storage<K>> topology;
    private final IdentityHashMap<Object, Long> reserved = new IdentityHashMap<>();
    private final List<Row<K>> rows = new ArrayList<>();
    private Iterator<K> keys;
    private int sourceIndex, keysChecked;
    private boolean done, limited;

    public PreviewPlanner(Predicate<K> filter, int rowLimit, int keyLimit) {
        this.filter = Objects.requireNonNull(filter);
        if (rowLimit < 1 || keyLimit < 1) throw new IllegalArgumentException("Invalid preview limits");
        this.rowLimit = rowLimit; this.keyLimit = keyLimit;
    }
    public boolean done() { return done; }
    public boolean limited() { return limited; }
    public List<Row<K>> rows() { return List.copyOf(rows); }
    public void step(List<RebalanceEngine.Storage<K>> mounts, int workLimit) {
        if (workLimit < 1) throw new IllegalArgumentException("Invalid work budget");
        if (done) return;
        var sorted = new ArrayList<>(mounts);
        sorted.sort(Comparator.comparingInt(RebalanceEngine.Storage<K>::priority));
        if (topology == null) {
            var seen = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
            for (var s : sorted) if (!seen.add(s.identity())) throw new IllegalStateException("Aliased storage mount");
        } else {
            if (sorted.size() != topology.size()) throw new IllegalStateException("Storage topology changed");
            for (int i = 0; i < sorted.size(); i++) {
                if (sorted.get(i).identity() != topology.get(i).identity()
                        || sorted.get(i).priority() != topology.get(i).priority())
                    throw new IllegalStateException("Storage topology changed");
            }
        }
        topology = sorted;
        int work = 0;
        while (sourceIndex < topology.size() && work++ < workLimit && !done) {
            var source = topology.get(sourceIndex);
            if (!source.available()) throw new IllegalStateException("Storage became unavailable");
            if (keys == null) keys = source.keys();
            if (!keys.hasNext()) { sourceIndex++; keys = null; continue; }
            if (keysChecked++ >= keyLimit) { limited = done = true; break; }
            var key = keys.next();
            if (!filter.test(key)) continue;
            long remaining = count(source.extract(key, Long.MAX_VALUE, true), Long.MAX_VALUE);
            if (remaining == 0) continue;
            var destinations = new ArrayList<RebalanceEngine.Storage<K>>();
            for (var destination : topology) if (destination.priority() > source.priority()) destinations.add(destination);
            destinations.sort(Comparator.comparingInt(RebalanceEngine.Storage<K>::priority).reversed()
                    .thenComparing(s -> !s.preferred(key)));
            for (var destination : destinations) {
                if (remaining == 0) break;
                if (!destination.available()) throw new IllegalStateException("Destination became unavailable");
                long capacity = count(destination.insert(key, Long.MAX_VALUE, true), Long.MAX_VALUE);
                // Generic handlers cannot simulate a whole transaction across item types. Deduct
                // ALL earlier incoming reservations, conservatively avoiding double-counted space.
                long used = reserved.getOrDefault(destination.identity(), 0L);
                long amount = Math.min(remaining, Math.max(0, capacity - used));
                if (amount == 0) continue;
                int index = -1;
                for (int i = 0; i < rows.size(); i++) {
                    var row = rows.get(i);
                    if (row.key().equals(key) && row.destination().identity() == destination.identity()) { index = i; break; }
                }
                if (index < 0 && rows.size() >= rowLimit) { limited = done = true; break; }
                if (index >= 0) rows.set(index, new Row<>(key, Math.addExact(rows.get(index).amount(), amount), destination));
                else rows.add(new Row<>(key, amount, destination));
                reserved.put(destination.identity(), Math.addExact(used, amount));
                remaining -= amount;
            }
        }
        if (sourceIndex >= topology.size()) done = true;
    }
    private static long count(long value, long requested) {
        if (value < 0 || value > requested) throw new IllegalStateException("Storage violated simulation contract");
        return value;
    }
}
