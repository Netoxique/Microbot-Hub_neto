package net.runelite.client.plugins.microbot.netomahoganyhomes;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;

final class NetoMahoganyHomesNavigation {
    static final int NOELLA_FIRST_UP_STAIRS = 17026;
    static final int NOELLA_FIRST_DOWN_STAIRS = 16685;
    static final int NOELLA_SECOND_UP_STAIRS = 15645;
    static final int NOELLA_SECOND_DOWN_STAIRS = 15648;

    private NetoMahoganyHomesNavigation() {
    }

    static boolean shouldDirectlyInteractWithStairs(Home home) {
        return home == Home.LARRY || home == Home.LEELA;
    }

    static boolean isReachablePath(int pathDistance) {
        return pathDistance != Integer.MAX_VALUE;
    }

    static boolean isRepairDestinationReached(int currentPlane, int targetPlane, int pathDistance,
                                               int reachedDistance) {
        return currentPlane == targetPlane && isReachablePath(pathDistance) && pathDistance <= reachedDistance;
    }

    static boolean shouldOpenBankDirectly(boolean usingDuelingRing, BankLocation bankLocation) {
        return usingDuelingRing && bankLocation == BankLocation.CASTLE_WARS;
    }

    static boolean shouldUseAreaArrival(Home home) {
        return home != Home.ROSS;
    }

    static int selectNoellaUpStairs(WorldPoint targetLocation, WorldPoint firstSectionReference,
                                     WorldPoint secondSectionReference) {
        if (targetLocation == null) {
            return -1;
        }
        if (firstSectionReference == null) {
            return secondSectionReference == null ? -1 : NOELLA_SECOND_UP_STAIRS;
        }
        if (secondSectionReference == null) {
            return NOELLA_FIRST_UP_STAIRS;
        }

        int firstDistance = firstSectionReference.distanceTo2D(targetLocation);
        int secondDistance = secondSectionReference.distanceTo2D(targetLocation);
        return firstDistance <= secondDistance ? NOELLA_FIRST_UP_STAIRS : NOELLA_SECOND_UP_STAIRS;
    }

    static int getNoellaDownStairsForUpStairs(int upStairsId) {
        if (upStairsId == NOELLA_FIRST_UP_STAIRS) {
            return NOELLA_FIRST_DOWN_STAIRS;
        }
        if (upStairsId == NOELLA_SECOND_UP_STAIRS) {
            return NOELLA_SECOND_DOWN_STAIRS;
        }
        return -1;
    }

    static boolean shouldWalkToNoellaUpStairs(int upStairsId) {
        return upStairsId != NOELLA_SECOND_UP_STAIRS;
    }
}
