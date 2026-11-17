package dev.functionalae.rebalancer.core;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.function.Predicate;
import static org.junit.jupiter.api.Assertions.*;

class RebalanceEngineTest {
    private static final class Store implements RebalanceEngine.Storage<String> {
        final Map<String, Long> stock = new LinkedHashMap<>();
        final int priority;
        long capacity = Long.MAX_VALUE;
        long actualInsertLimit = Long.MAX_VALUE;
        boolean valid = true, preferred;
        int extracts, inserts;
        Store(int priority) { this.priority = priority; }
        Store with(String key, long amount) { stock.put(key, amount); return this; }
        long count(String key) { return stock.getOrDefault(key, 0L); }
        long total() { return stock.values().stream().mapToLong(Long::longValue).sum(); }
        public Object identity() { return this; }
        public int priority() { return priority; }
        public boolean available() { return valid; }
        public Iterator<String> keys() { return new ArrayList<>(stock.keySet()).iterator(); }
        public boolean preferred(String key) { return preferred; }
        public long extract(String key, long amount, boolean simulate) {
            long take = Math.min(count(key), amount);
            if (!simulate) { extracts++; stock.put(key, count(key) - take); }
            return take;
        }
        public long insert(String key, long amount, boolean simulate) {
            long accept = Math.max(0, Math.min(amount, capacity - total()));
            if (!simulate) { inserts++; accept = Math.min(accept, actualInsertLimit); stock.put(key, count(key) + accept); }
            return accept;
        }
    }
    private RebalanceEngine<String> engine() { return new RebalanceEngine<>(() -> {}); }
    private RebalanceEngine.Result tick(RebalanceEngine<String> engine, List<RebalanceEngine.Storage<String>> stores) {
        return engine.tick(stores, key -> true, 4096, 64, requested -> requested);
    }
    @Test void migratesLargeCountsWithoutRemovingCells() {
        var cell = new Store(0).with("iron", 100_000);
        var drawer = new Store(100);
        var engine = engine();
        var stores = List.<RebalanceEngine.Storage<String>>of(cell, drawer);
        boolean settled = false;
        for (int i = 0; i < 100; i++) {
            var result = tick(engine, stores);
            assertTrue(result.moved() <= 4096);
            assertEquals(100_000, cell.total() + drawer.total());
            if (result.settled()) { settled = true; break; }
        }
        assertTrue(settled); assertEquals(0, cell.count("iron")); assertEquals(100_000, drawer.count("iron"));
    }
    @Test void equalPriorityNeverChurns() {
        var a = new Store(0).with("iron", 100); var b = new Store(0);
        var result = tick(engine(), List.of(a,b));
        assertTrue(result.settled()); assertEquals(0, a.extracts + b.extracts + a.inserts + b.inserts);
    }
    @Test void fullHigherPriorityStorageSettlesWithoutExtraction() {
        var a = new Store(0).with("iron", 100); var b = new Store(100); b.capacity = 0;
        assertTrue(tick(engine(), List.of(a,b)).settled()); assertEquals(0, a.extracts);
    }
    @Test void takesHighestPriorityThenFallsBackToOtherHigherPriorities() {
        var a = new Store(-100).with("iron", 100); var b = new Store(0); var c = new Store(Integer.MAX_VALUE);
        c.capacity = 25;
        tick(engine(), List.of(a,b,c));
        assertEquals(25, c.count("iron")); assertEquals(75, b.count("iron"));
    }
    @Test void preferredWinsOnlyWithinItsPriorityGroup() {
        var a = new Store(0).with("iron", 100); var b = new Store(100); var c = new Store(100); c.preferred = true;
        tick(engine(), List.of(a,b,c)); assertEquals(100, c.count("iron")); assertEquals(0, b.total());
    }
    @Test void itemFilterPreservesUnselectedStock() {
        var a = new Store(0).with("iron", 100).with("gold", 200); var b = new Store(100);
        engine().tick(List.of(a,b), "iron"::equals, 4096, 64, n -> n);
        assertEquals(100, b.count("iron")); assertEquals(200, a.count("gold")); assertEquals(0, b.count("gold"));
    }
    @Test void powerOutageDoesNotFalselyCompleteRunOnce() {
        var a = new Store(0).with("iron", 100); var b = new Store(100);
        var result = engine().tick(List.of(a,b), k -> true, 4096, 64, n -> 0);
        assertFalse(result.settled()); assertEquals(0, a.extracts);
    }
    @Test void partialPowerFundsOnlyWhatCanMove() {
        var a = new Store(0).with("iron", 100); var b = new Store(100);
        engine().tick(List.of(a,b), k -> true, 4096, 64, n -> Math.min(n, 17));
        assertEquals(17, b.total()); assertEquals(83, a.total());
    }
    @Test void unexpectedShortInsertionRollsBackToSource() {
        var a = new Store(0).with("iron", 100); var b = new Store(100); b.actualInsertLimit = 7;
        var engine = engine(); tick(engine, List.of(a,b));
        assertNull(engine.pending()); assertEquals(100, a.total() + b.total()); assertEquals(7, b.total());
    }
    @Test void failedRollbackPersistsRemainderAndRecoversAfterRestart() {
        var a = new Store(0).with("iron", 100); a.actualInsertLimit = 0;
        var b = new Store(100); b.actualInsertLimit = 7;
        var engine = engine(); tick(engine, List.of(a,b));
        assertEquals(93, engine.pending().amount()); assertEquals(100, a.total() + b.total() + engine.pending().amount());
        var restored = engine(); restored.restore(engine.pending()); b.actualInsertLimit = Long.MAX_VALUE;
        tick(restored, List.of(b)); assertNull(restored.pending()); assertEquals(100, b.total());
    }
    @Test void unavailableDestinationNeverExtracts() {
        var a = new Store(0).with("iron", 100); var b = new Store(100); b.valid = false;
        tick(engine(), List.of(a,b)); assertEquals(0, a.extracts);
    }
    @Test void topologyChangesDiscardStaleScan() {
        var a = new Store(0).with("iron", 100); var b = new Store(100);
        var engine = engine(); engine.tick(List.of(a,b), k -> true, 1, 1, n -> n);
        b.valid = false; var c = new Store(200);
        for (int i = 0; i < 5; i++) tick(engine, List.of(a,c));
        assertEquals(99, c.total()); assertEquals(1, b.total());
    }
    @Test void newIncomingItemsCanBeMigratedAfterSettling() {
        var a = new Store(0); var b = new Store(100); var engine = engine();
        assertTrue(tick(engine, List.of(a,b)).settled());
        a.with("iron", 100); tick(engine, List.of(a,b)); assertEquals(100, b.total());
    }
    @Test void duplicateMountFailsBeforeMutation() {
        var a = new Store(0).with("iron", 100);
        assertThrows(IllegalArgumentException.class, () -> tick(engine(), List.of(a,a)));
        assertEquals(100, a.total()); assertEquals(0, a.extracts);
    }
    @Test void workBudgetContinuesAcrossTicksWithoutStarvingLaterKeys() {
        var a = new Store(0).with("a", 1).with("b", 1).with("c", 1); var b = new Store(1); var engine = engine();
        for (int i = 0; i < 20; i++) {
            var result = engine.tick(List.of(a,b), k -> true, 1, 1, n -> n);
            assertTrue(result.checked() <= 1); assertTrue(result.moved() <= 1);
        }
        assertEquals(3, b.total()); assertEquals(0, a.total());
    }
}
