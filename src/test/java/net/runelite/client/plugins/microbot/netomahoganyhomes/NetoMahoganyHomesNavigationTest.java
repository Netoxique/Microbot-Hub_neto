package net.runelite.client.plugins.microbot.netomahoganyhomes;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetoMahoganyHomesNavigationTest {
    private static Stream<Arguments> homeArrivalAreas() {
        return Stream.of(
                Arguments.of(Home.LARRY, 3036, 3362, 3040, 3364),
                Arguments.of(Home.NORMAN, 3036, 3344, 3038, 3346),
                Arguments.of(Home.TAU, 3046, 3345, 3049, 3348),
                Arguments.of(Home.NOELLA, 2657, 3321, 2660, 3322),
                Arguments.of(Home.JESS, 2621, 3292, 2622, 3294),
                Arguments.of(Home.ROSS, 2611, 3315, 2613, 3318),
                Arguments.of(Home.JEFF, 3239, 3449, 3242, 3453),
                Arguments.of(Home.BOB, 3239, 3484, 3241, 3487),
                Arguments.of(Home.SARAH, 3234, 3383, 3237, 3386),
                Arguments.of(Home.BARBARA, 1747, 3533, 1753, 3535),
                Arguments.of(Home.LEELA, 1783, 3592, 1786, 3594),
                Arguments.of(Home.MARIAH, 1764, 3620, 1767, 3623));
    }

    @ParameterizedTest
    @MethodSource("homeArrivalAreas")
    void detectsOnlyTilesInsidePlaneAwareArrivalArea(Home home, int southWestX, int southWestY,
                                                     int northEastX, int northEastY) {
        assertTrue(home.hasArrived(new WorldPoint(southWestX, southWestY, 0)));
        assertTrue(home.hasArrived(new WorldPoint(northEastX, northEastY, 0)));
        assertTrue(home.hasArrived(new WorldPoint((southWestX + northEastX) / 2,
                (southWestY + northEastY) / 2, 0)));

        assertFalse(home.hasArrived(new WorldPoint(southWestX - 1, southWestY, 0)));
        assertFalse(home.hasArrived(new WorldPoint(northEastX + 1, northEastY, 0)));
        assertFalse(home.hasArrived(new WorldPoint(southWestX, southWestY, 1)));
    }

    @Test
    void daleArrivalCoordinatesMapToBarbara() {
        assertTrue(Home.BARBARA.hasArrived(new WorldPoint(1750, 3534, 0)));
    }

    @Test
    void insideCheckOnlyUsesSelectedHomeArea() {
        assertTrue(Home.LARRY.isInside(new WorldPoint(3038, 3364, 0)));
        assertFalse(Home.LARRY.isInside(new WorldPoint(3038, 3344, 0)));
    }

    @Test
    void duelingRingRouteOpensCastleWarsBankDirectly() {
        assertTrue(NetoMahoganyHomesNavigation.shouldOpenBankDirectly(true, BankLocation.CASTLE_WARS));
        assertFalse(NetoMahoganyHomesNavigation.shouldOpenBankDirectly(false, BankLocation.CASTLE_WARS));
        assertFalse(NetoMahoganyHomesNavigation.shouldOpenBankDirectly(true, BankLocation.FALADOR_EAST));
        assertFalse(NetoMahoganyHomesNavigation.shouldOpenBankDirectly(true, null));
    }

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
