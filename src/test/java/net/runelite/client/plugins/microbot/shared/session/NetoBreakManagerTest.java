package net.runelite.client.plugins.microbot.shared.session;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetoBreakManagerTest {
    @Test
    void gameplayIsReadyOnlyAfterWelcomeAndBlockingEventsResolve() {
        assertFalse(NetoBreakManager.isGameplayReady(false, false, false, false));
        assertFalse(NetoBreakManager.isGameplayReady(true, true, false, true));
        assertFalse(NetoBreakManager.isGameplayReady(true, false, true, true));
        assertFalse(NetoBreakManager.isGameplayReady(true, false, false, false));
        assertTrue(NetoBreakManager.isGameplayReady(true, false, false, true));
    }
}
