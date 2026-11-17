package dev.functionalae.rebalancer.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ItemSelectionTest {
    @Test void emptySelectionNeverMovesEverything() {
        var selection = new ItemSelection<String>();
        assertFalse(selection.canRun()); assertFalse(selection.matches("iron")); assertFalse(selection.allItems());
    }
    @Test void allItemsRequiresExplicitChoiceAndKeepsWhitelist() {
        var selection = new ItemSelection<String>(); selection.set(20, "iron");
        selection.setAllItems(true); assertTrue(selection.matches("gold"));
        selection.setAllItems(false); assertTrue(selection.matches("iron")); assertFalse(selection.matches("gold"));
    }
    @Test void clearingTheLastFilterDisablesRunInsteadOfSelectingAll() {
        var selection = new ItemSelection<String>(); selection.set(63, "iron"); selection.set(63, null);
        assertFalse(selection.canRun()); assertFalse(selection.matches("iron"));
    }
    @Test void clearResetsExplicitAllItemsModeToo() {
        var selection = new ItemSelection<String>(); selection.add("iron"); selection.setAllItems(true); selection.clear();
        assertFalse(selection.canRun()); assertEquals(0, selection.count()); assertFalse(selection.matches("gold"));
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
        var selection = new ItemSelection<Variant>(); selection.add(new Variant("potion", "healing"));
        assertTrue(selection.matches(new Variant("potion", "healing")));
        assertFalse(selection.matches(new Variant("potion", "poison")));
    }
}
