package net.runelite.client.plugins.microbot.netosailingsalv.features.salvaging;

import net.runelite.http.api.worlds.WorldRegion;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SalvagingScriptTest {

    @Test
    void parsesConfiguredMinimumAlchValue() {
        assertEquals(2000, SalvagingScript.parseMinimumAlchValue(null));
        assertEquals(2000, SalvagingScript.parseMinimumAlchValue("not a number"));
        assertEquals(2000, SalvagingScript.parseMinimumAlchValue("-1"));
        assertEquals(0, SalvagingScript.parseMinimumAlchValue("0"));
        assertEquals(3456, SalvagingScript.parseMinimumAlchValue(" 3456 "));
    }

    @Test
    void recognizesOnlySupportedCarriedBankTeleports() {
        assertTrue(SalvagingScript.isValidBankTeleportName("Construction cape"));
        assertTrue(SalvagingScript.isValidBankTeleportName("Construction cape(t)"));
        assertTrue(SalvagingScript.isValidBankTeleportName("Crafting cape"));
        assertTrue(SalvagingScript.isValidBankTeleportName("Crafting cape(t)"));
        assertTrue(SalvagingScript.isValidBankTeleportName("Farming cape"));
        assertTrue(SalvagingScript.isValidBankTeleportName("Farming cape(t)"));
        assertTrue(SalvagingScript.isValidBankTeleportName("Sailors' amulet"));
        assertTrue(SalvagingScript.isValidBankTeleportName("Skills necklace(1)"));
        assertTrue(SalvagingScript.isValidBankTeleportName("Ring of dueling(8)"));
        assertFalse(SalvagingScript.isValidBankTeleportName("Skills necklace"));
        assertFalse(SalvagingScript.isValidBankTeleportName("Ring of wealth(5)"));
        assertFalse(SalvagingScript.isValidBankTeleportName(null));
    }

    @Test
    void usesFifteenTilesAsTheSalvagingBoundary() {
        assertTrue(SalvagingScript.isWithinSalvageDistance(0));
        assertTrue(SalvagingScript.isWithinSalvageDistance(15));
        assertFalse(SalvagingScript.isWithinSalvageDistance(16));
    }

    @Test
    void prioritizesSalvagingAndUnitedStatesWorlds() {
        assertEquals(0, SalvagingScript.worldPriority("Salvaging", WorldRegion.UNITED_STATES_OF_AMERICA));
        assertEquals(0, SalvagingScript.worldPriority(" salvaging ", WorldRegion.UNITED_STATES_OF_AMERICA));
        assertEquals(1, SalvagingScript.worldPriority("Salvaging", WorldRegion.UNITED_KINGDOM));
        assertEquals(2, SalvagingScript.worldPriority("Trade", WorldRegion.UNITED_STATES_OF_AMERICA));
        assertEquals(Integer.MAX_VALUE, SalvagingScript.worldPriority("Trade", WorldRegion.UNITED_KINGDOM));
    }
}
