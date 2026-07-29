package net.runelite.client.plugins.microbot.netoherbrun;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetoHerbrunScriptTest {
    @Test
    void allFourReadyBirdhousesAreAccepted() {
        assertTrue(NetoHerbrunScript.allBirdhousesReady(3, 6, 9, 12));
    }

    @Test
    void emptyBirdhouseIsNotReady() {
        assertFalse(NetoHerbrunScript.allBirdhousesReady(3, 6, 0, 12));
    }

    @Test
    void builtOrGrowingBirdhouseIsNotReady() {
        assertFalse(NetoHerbrunScript.allBirdhousesReady(3, 6, 10, 12));
    }

    @Test
    void mixedBirdhouseStatesAreNotReady() {
        assertFalse(NetoHerbrunScript.allBirdhousesReady(1, 6, 9, 12));
    }

    @Test
    void incompleteBirdhouseStateIsNotReady() {
        assertFalse(NetoHerbrunScript.allBirdhousesReady(3, 6, 9));
    }
}
