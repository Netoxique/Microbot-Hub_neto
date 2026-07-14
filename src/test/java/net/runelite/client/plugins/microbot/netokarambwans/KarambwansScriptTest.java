package net.runelite.client.plugins.microbot.netokarambwans;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KarambwansScriptTest {
    @Test
    void walkingStateOnlyAdvancesAfterArrivalOrFishingStarts() {
        assertFalse(KarambwansScript.shouldAdvanceToFishing(false, false));
        assertTrue(KarambwansScript.shouldAdvanceToFishing(true, false));
        assertTrue(KarambwansScript.shouldAdvanceToFishing(false, true));
    }
}
