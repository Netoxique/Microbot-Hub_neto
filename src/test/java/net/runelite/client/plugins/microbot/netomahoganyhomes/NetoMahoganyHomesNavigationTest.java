package net.runelite.client.plugins.microbot.netomahoganyhomes;

import net.runelite.api.coords.WorldPoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetoMahoganyHomesNavigationTest {
    @Test
    void selectsFirstNoellaStairsForFirstSectionTarget() {
        WorldPoint target = new WorldPoint(10, 10, 1);
        WorldPoint firstSectionStairs = new WorldPoint(11, 10, 1);
        WorldPoint secondSectionStairs = new WorldPoint(20, 20, 1);

        assertEquals(NetoMahoganyHomesNavigation.NOELLA_FIRST_UP_STAIRS,
                NetoMahoganyHomesNavigation.selectNoellaUpStairs(
                        target, firstSectionStairs, secondSectionStairs));
    }

    @Test
    void selectsSecondNoellaStairsForSecondSectionTarget() {
        WorldPoint target = new WorldPoint(20, 19, 1);
        WorldPoint firstSectionStairs = new WorldPoint(10, 10, 1);
        WorldPoint secondSectionStairs = new WorldPoint(20, 20, 1);

        assertEquals(NetoMahoganyHomesNavigation.NOELLA_SECOND_UP_STAIRS,
                NetoMahoganyHomesNavigation.selectNoellaUpStairs(
                        target, firstSectionStairs, secondSectionStairs));
    }

    @Test
    void pairsFirstNoellaUpAndDownStairs() {
        assertEquals(NetoMahoganyHomesNavigation.NOELLA_FIRST_DOWN_STAIRS,
                NetoMahoganyHomesNavigation.getNoellaDownStairsForUpStairs(
                        NetoMahoganyHomesNavigation.NOELLA_FIRST_UP_STAIRS));
    }

    @Test
    void pairsSecondNoellaUpAndDownStairs() {
        assertEquals(NetoMahoganyHomesNavigation.NOELLA_SECOND_DOWN_STAIRS,
                NetoMahoganyHomesNavigation.getNoellaDownStairsForUpStairs(
                        NetoMahoganyHomesNavigation.NOELLA_SECOND_UP_STAIRS));
    }

    @Test
    void onlySecondNoellaUpStairsBypassesExplicitWalking() {
        assertTrue(NetoMahoganyHomesNavigation.shouldWalkToNoellaUpStairs(
                NetoMahoganyHomesNavigation.NOELLA_FIRST_UP_STAIRS));
        assertFalse(NetoMahoganyHomesNavigation.shouldWalkToNoellaUpStairs(
                NetoMahoganyHomesNavigation.NOELLA_SECOND_UP_STAIRS));
    }

    @Test
    void unreachableGeometricallyCloseTargetIsNotReached() {
        assertFalse(NetoMahoganyHomesNavigation.isRepairDestinationReached(
                1, 1, Integer.MAX_VALUE, 3));
    }

    @Test
    void reachableTargetWithinThresholdIsReached() {
        assertTrue(NetoMahoganyHomesNavigation.isRepairDestinationReached(1, 1, 3, 3));
    }

    @Test
    void onlyLarryAndLeelaUseDirectStairInteractionPolicy() {
        assertTrue(NetoMahoganyHomesNavigation.shouldDirectlyInteractWithStairs(Home.LARRY));
        assertTrue(NetoMahoganyHomesNavigation.shouldDirectlyInteractWithStairs(Home.LEELA));
        assertFalse(NetoMahoganyHomesNavigation.shouldDirectlyInteractWithStairs(Home.MARIAH));
        assertFalse(NetoMahoganyHomesNavigation.shouldDirectlyInteractWithStairs(Home.NOELLA));
    }
}
