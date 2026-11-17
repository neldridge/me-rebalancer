package dev.functionalae.rebalancer.core;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PreviewPlannerTest {
    private static final class Store implements RebalanceEngine.Storage<String> {
        final int priority;
        final Map<String, Long> stock = new LinkedHashMap<>();
        long capacity = Long.MAX_VALUE;
        boolean preferred, available = true;
        Store(int priority) { this.priority = priority; }
        Store with(String key, long count) { stock.put(key, count); return this; }
        public Object identity() { return this; }
        public int priority() { return priority; }
        public boolean available() { return available; }
        public Iterator<String> keys() { return new ArrayList<>(stock.keySet()).iterator(); }
        public boolean preferred(String key) { return preferred; }
        public long extract(String key, long count, boolean simulate) {
            assertTrue(simulate, "Preview must never extract real stock");
            return Math.min(count, stock.getOrDefault(key, 0L));
        }
        public long insert(String key, long count, boolean simulate) {
            assertTrue(simulate, "Preview must never insert real stock");
            long used = stock.values().stream().mapToLong(Long::longValue).sum();
            return Math.max(0, Math.min(count, capacity - used));
        }
    }
    private PreviewPlanner<String> complete(List<RebalanceEngine.Storage<String>> mounts) {
        var plan = new PreviewPlanner<String>(k -> true, 256, 4096);
        for (int i = 0; i < 100 && !plan.done(); i++) plan.step(mounts, 8);
        assertTrue(plan.done()); return plan;
    }
    @Test void simulationOnlyRespectsDestinationCapacityAndLeavesItemsUntouched() {
        var source = new Store(0).with("iron", 123_456); var target = new Store(100); target.capacity = 50_000;
        var plan = complete(List.of(source, target));
        assertEquals(50_000, plan.rows().getFirst().amount()); assertEquals(123_456, source.stock.get("iron")); assertTrue(target.stock.isEmpty());
    }
    @Test void neverCountsTheSameDestinationCapacityTwiceAcrossSources() {
        var a = new Store(0).with("iron", 30); var b = new Store(0).with("iron", 40); var target = new Store(100); target.capacity = 64;
        var plan = complete(List.of(a,b,target));
        assertEquals(1, plan.rows().size()); assertEquals(64, plan.rows().getFirst().amount());
    }
    @Test void sharedCapacityIsConservativelyReservedAcrossDifferentItemTypes() {
        var a = new Store(0).with("iron", 64).with("gold", 64); var target = new Store(100); target.capacity = 64;
        var plan = complete(List.of(a,target));
        assertEquals(64, plan.rows().stream().mapToLong(PreviewPlanner.Row::amount).sum());
    }
    @Test void choosesHighestPriorityThenPreferredDestination() {
        var a = new Store(Integer.MIN_VALUE).with("iron", 100);
        var b = new Store(100); var c = new Store(100); c.preferred = true;
        var lower = new Store(50);
        var plan = complete(List.of(a,b,c,lower));
        assertEquals(1, plan.rows().size()); assertSame(c, plan.rows().getFirst().destination());
    }
    @Test void equalPriorityAndFullTargetsProduceNoMoves() {
        var a = new Store(0).with("iron", 100); var b = new Store(0); var c = new Store(100); c.capacity = 0;
        assertTrue(complete(List.of(a,b,c)).rows().isEmpty());
    }
    @Test void previewsSplitAcrossMultipleDestinations() {
        var a = new Store(0).with("iron", 100); var b = new Store(10); b.capacity = 40; var c = new Store(20); c.capacity = 30;
        var plan = complete(List.of(a,b,c));
        assertEquals(2, plan.rows().size()); assertEquals(70, plan.rows().stream().mapToLong(PreviewPlanner.Row::amount).sum());
    }
    @Test void respectsItemFilter() {
        var a = new Store(0).with("iron", 100).with("gold", 100); var b = new Store(10);
        var plan = new PreviewPlanner<String>("gold"::equals, 256, 4096);
        plan.step(List.of(a,b), 8); assertTrue(plan.done()); assertEquals("gold", plan.rows().getFirst().key()); assertEquals(1, plan.rows().size());
    }
    @Test void preservesLongAmounts() {
        long large = 9_000_000_123_456_789L;
        var a = new Store(0).with("iron", large); var b = new Store(10);
        assertEquals(large, complete(List.of(a,b)).rows().getFirst().amount());
    }
    @Test void topologyChangesAbortInsteadOfPublishingMixedRoutes() {
        var a = new Store(0).with("iron", 100); var b = new Store(10); var c = new Store(20);
        var plan = new PreviewPlanner<String>(k -> true, 256, 4096); plan.step(List.of(a,b), 1);
        assertThrows(IllegalStateException.class, () -> plan.step(List.of(a,c), 8));
    }
    @Test void rowAndKeyLimitsAreExplicitlyReported() {
        var a = new Store(0).with("iron", 10).with("gold", 10); var b = new Store(10);
        var rows = new PreviewPlanner<String>(k -> true, 1, 4096); rows.step(List.of(a,b), 8);
        assertTrue(rows.done() && rows.limited()); assertEquals(1, rows.rows().size());
        var keys = new PreviewPlanner<String>(k -> true, 256, 1); keys.step(List.of(a,b), 8);
        assertTrue(keys.done() && keys.limited()); assertEquals(1, keys.rows().size());
    }
    @Test void workContinuesIncrementally() {
        var a = new Store(0).with("iron", 10).with("gold", 10); var b = new Store(10);
        var plan = new PreviewPlanner<String>(k -> true, 256, 4096); plan.step(List.of(a,b), 1);
        assertFalse(plan.done());
        for (int i = 0; i < 10 && !plan.done(); i++) plan.step(List.of(a,b), 1);
        assertTrue(plan.done()); assertEquals(2, plan.rows().size());
    }
}
