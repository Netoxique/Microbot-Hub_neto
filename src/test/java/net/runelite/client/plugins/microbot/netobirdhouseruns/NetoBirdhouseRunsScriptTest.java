package net.runelite.client.plugins.microbot.netobirdhouseruns;

import net.runelite.client.plugins.microbot.netobirdhouseruns.enums.Log;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NetoBirdhouseRunsScriptTest {
    @Test
    void selectsEachLogAtItsHunterAndCraftingThreshold() {
        assertEquals(Log.NORMAL_LOGS, NetoBirdhouseRunsScript.getBestLogType(5, 5));
        assertEquals(Log.OAK_LOGS, NetoBirdhouseRunsScript.getBestLogType(14, 15));
        assertEquals(Log.WILLOW_LOGS, NetoBirdhouseRunsScript.getBestLogType(24, 25));
        assertEquals(Log.TEAK_LOGS, NetoBirdhouseRunsScript.getBestLogType(34, 35));
        assertEquals(Log.MAPLE_LOGS, NetoBirdhouseRunsScript.getBestLogType(44, 45));
        assertEquals(Log.MAHOGANY_LOGS, NetoBirdhouseRunsScript.getBestLogType(49, 50));
        assertEquals(Log.YEW_LOGS, NetoBirdhouseRunsScript.getBestLogType(59, 60));
        assertEquals(Log.MAGIC_LOGS, NetoBirdhouseRunsScript.getBestLogType(74, 75));
        assertEquals(Log.REDWOOD_LOGS, NetoBirdhouseRunsScript.getBestLogType(89, 90));
    }

    @Test
    void lowerSkillControlsWhenLevelsAreMismatched() {
        assertEquals(Log.NORMAL_LOGS, NetoBirdhouseRunsScript.getBestLogType(99, 14));
        assertEquals(Log.NORMAL_LOGS, NetoBirdhouseRunsScript.getBestLogType(13, 99));
        assertEquals(Log.MAGIC_LOGS, NetoBirdhouseRunsScript.getBestLogType(99, 89));
        assertEquals(Log.MAGIC_LOGS, NetoBirdhouseRunsScript.getBestLogType(88, 99));
    }
}
