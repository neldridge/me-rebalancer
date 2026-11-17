package dev.functionalae.rebalancer.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ItemSelectionTest {
    @Test void defaultsToAllItems() {
        var selection = new ItemSelection<String>();
        assertTrue(selection.canRun()); assertTrue(selection.matches("iron")); assertTrue(selection.allItems());
    }
    @Test void modesCycleAndKeepFilters() {
        var selection = new ItemSelection<String>(); selection.set(20, "iron");
        selection.nextMode();
        assertEquals(ItemSelection.Mode.WHITELIST, selection.mode());
        assertTrue(selection.matches("iron")); assertFalse(selection.matches("gold"));
        selection.nextMode();
        assertEquals(ItemSelection.Mode.BLACKLIST, selection.mode());
        assertFalse(selection.matches("iron")); assertTrue(selection.matches("gold"));
        selection.nextMode(); assertTrue(selection.allItems()); assertTrue(selection.matches("iron"));
        assertEquals("iron", selection.get(20));
    }
    @Test void emptyWhitelistCannotRunButEmptyBlacklistCan() {
        var selection = new ItemSelection<String>();
        selection.setMode(ItemSelection.Mode.WHITELIST);
        selection.set(63, "iron"); selection.set(63, null);
        assertFalse(selection.canRun()); assertFalse(selection.matches("iron"));
        selection.setMode(ItemSelection.Mode.BLACKLIST);
        assertTrue(selection.canRun()); assertTrue(selection.matches("iron"));
    }
    @Test void clearPreservesMode() {
        var selection = new ItemSelection<String>();
        for (var mode : ItemSelection.Mode.values()) {
            selection.setMode(mode); selection.add("iron"); selection.clear();
            assertEquals(mode, selection.mode()); assertEquals(0, selection.count());
            assertEquals(mode != ItemSelection.Mode.WHITELIST, selection.matches("gold"));
        }
    }
    @Test void movingDuplicateFilterAcrossPagesKeepsOnlyOneEntry() {
        var selection = new ItemSelection<String>(); selection.set(0, "iron"); selection.set(63, "iron");
        assertNull(selection.get(0)); assertEquals("iron", selection.get(63)); assertEquals(1, selection.count());
    }
    @Test void fillingAllSlotsNeverReplacesAnExistingFilter() {
        var selection = new ItemSelection<String>();
        for (int i = 0; i < 64; i++) assertTrue(selection.add("item" + i));
        assertFalse(selection.add("overflow")); assertFalse(selection.add("item0"));
        assertEquals("item0", selection.get(0)); assertEquals("item63", selection.get(63)); assertEquals(64, selection.count());
    }
    @Test void itemComponentsRemainPartOfExactMatch() {
        record Variant(String item, String component) {}
        var selection = new ItemSelection<Variant>(); selection.setMode(ItemSelection.Mode.WHITELIST); selection.add(new Variant("potion", "healing"));
        assertTrue(selection.matches(new Variant("potion", "healing")));
        assertFalse(selection.matches(new Variant("potion", "poison")));
    }
}
