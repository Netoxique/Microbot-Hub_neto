package net.runelite.client.plugins.microbot.shared.session;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.misc.TimeUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.util.Random;

@Singleton
@Slf4j
public class NetoRuntimeDisable {

    private final Random random = new Random();
    private RuntimeSettings settings;
    private Instant shutdownTime;
    private String logPrefix = "Neto";
    private boolean hasLoggedOut = false;

    @Inject
    public NetoRuntimeDisable() {}

    public synchronized void configure(RuntimeSettings settings, String logPrefix) {
        this.settings = settings;
        this.logPrefix = logPrefix == null || logPrefix.trim().isEmpty() ? "Neto" : logPrefix.trim();
    }

    public synchronized void reset() {
        hasLoggedOut = false;
        if (settings == null || !settings.enableRuntime()) {
            shutdownTime = null;
            return;
        }

        int min = settings.minRuntime();
        int max = settings.maxRuntime();

        if (min <= 0 && max <= 0) {
            shutdownTime = null;
            return;
        }

        int targetMinutes;
        if (max > min) {
            targetMinutes = min + random.nextInt(max - min + 1);
        } else {
            targetMinutes = min;
        }

        shutdownTime = Instant.now().plusSeconds(targetMinutes * 60L);
        log.info("[{}] Plugin will shutdown in {} minutes.", logPrefix, targetMinutes);
    }

    public boolean updateRuntime(Class<? extends Plugin> pluginClass) {
        if (settings == null || shutdownTime == null) {
            return false;
        }

        if (Instant.now().isAfter(shutdownTime)) {
            if (!hasLoggedOut) {
                log.info("[{}] Runtime reached. Logging out and stopping plugin.", logPrefix);
                Rs2Player.logout();
                hasLoggedOut = true;
            } else if (!Microbot.isLoggedIn()) {
                Microbot.stopPlugin(pluginClass);
            }
            return true;
        }
        return false;
    }

    public String getShutdownInDisplay() {
        if (settings == null || shutdownTime == null) {
            return "Disabled";
        }
        if (Instant.now().isAfter(shutdownTime)) {
            return "Shutting down";
        }
        return TimeUtils.getFormattedDurationBetween(Instant.now(), shutdownTime);
    }
}
