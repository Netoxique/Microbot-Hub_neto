package net.runelite.client.plugins.microbot.netorc;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.security.Login;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Random;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

@Singleton
public class NetoRCBreakManager {
    private static final long MINUTE_MILLIS = 60_000L;
    private static final int LOGIN_TIMEOUT_MS = 15_000;
    private static final int LOGOUT_RETRY_DELAY_MS = 10_000;
    private static final int LOGIN_RETRY_DELAY_MS = 10_000;

    private final NetoRCConfig config;
    private final Random random = new Random();

    private long nextBreakAtMillis = 0;
    private long breakEndsAtMillis = 0;
    private long nextLogoutAttemptAtMillis = 0;
    private long nextLoginAttemptAtMillis = 0;
    private boolean breakActive = false;
    private int breakWorld = -1;
    private int totalBreaks = 0;

    @Inject
    public NetoRCBreakManager(NetoRCConfig config) {
        this.config = config;
    }

    public synchronized void reset() {
        nextBreakAtMillis = 0;
        breakEndsAtMillis = 0;
        nextLogoutAttemptAtMillis = 0;
        nextLoginAttemptAtMillis = 0;
        breakActive = false;
        breakWorld = -1;
        totalBreaks = 0;
    }

    public synchronized boolean updateBreakState() {
        long now = System.currentTimeMillis();

        if (!breakActive) {
            ensurePlayTimer();
            return false;
        }

        if (now < breakEndsAtMillis) {
            if (Microbot.isLoggedIn() && now >= nextLogoutAttemptAtMillis) {
                requestLogout();
            }
            return true;
        }

        if (Microbot.isLoggedIn()) {
            Microbot.log("Neto RC break window ended while still logged in. Scheduling next playtime.");
            finishBreak();
            return false;
        }

        if (!Microbot.isLoggedIn()) {
            if (now >= nextLoginAttemptAtMillis) {
                nextLoginAttemptAtMillis = now + LOGIN_RETRY_DELAY_MS;
                Microbot.log("Neto RC break complete, logging back into world " + breakWorld + ".");
                new Login(breakWorld);
                if (sleepUntil(Microbot::isLoggedIn, LOGIN_TIMEOUT_MS)) {
                    finishBreak();
                }
            }
            return true;
        }

        return true;
    }

    public synchronized boolean tryStartBreakAtBank() {
        ensurePlayTimer();
        long now = System.currentTimeMillis();

        if (!config.enableBreaks() || breakActive || nextBreakAtMillis <= 0 || now < nextBreakAtMillis) {
            return false;
        }

        int durationMinutes = randomBetween(config.minBreak(), config.maxBreak());
        breakWorld = Rs2Player.getWorld();
        breakEndsAtMillis = now + durationMinutes * MINUTE_MILLIS;
        nextLogoutAttemptAtMillis = 0;
        nextLoginAttemptAtMillis = 0;
        breakActive = true;
        totalBreaks++;

        Microbot.log("Neto RC starting logout break for " + durationMinutes + " minutes on world " + breakWorld + ".");
        requestLogout();
        return true;
    }

    public synchronized String getBreakInDisplay() {
        ensurePlayTimer();

        if (breakActive) {
            return "On break " + formatRemaining(breakEndsAtMillis - System.currentTimeMillis());
        }

        if (!config.enableBreaks()) {
            return "Disabled";
        }

        if (nextBreakAtMillis <= 0) {
            return "Pending";
        }

        return formatRemaining(nextBreakAtMillis - System.currentTimeMillis());
    }

    public synchronized int getTotalBreaks() {
        return totalBreaks;
    }

    public synchronized boolean isBreakActive() {
        return breakActive;
    }

    private void finishBreak() {
        Microbot.log("Neto RC break finished. Scheduling next playtime.");
        breakActive = false;
        breakEndsAtMillis = 0;
        breakWorld = -1;
        nextLogoutAttemptAtMillis = 0;
        nextLoginAttemptAtMillis = 0;
        scheduleNextPlayTimer();
    }

    private void requestLogout() {
        nextLogoutAttemptAtMillis = System.currentTimeMillis() + LOGOUT_RETRY_DELAY_MS;

        try {
            Rs2Player.logout();
        } catch (Exception ex) {
            Microbot.log("Neto RC logout request failed, retrying soon: " + ex.getMessage());
        }
    }

    private void ensurePlayTimer() {
        if (!config.enableBreaks()) {
            if (!breakActive) {
                nextBreakAtMillis = 0;
            }
            return;
        }

        if (!breakActive && nextBreakAtMillis <= 0) {
            scheduleNextPlayTimer();
        }
    }

    private void scheduleNextPlayTimer() {
        if (!config.enableBreaks()) {
            nextBreakAtMillis = 0;
            return;
        }

        int playMinutes = randomBetween(config.minPlaytime(), config.maxPlaytime());
        nextBreakAtMillis = System.currentTimeMillis() + playMinutes * MINUTE_MILLIS;
        Microbot.log("Neto RC next logout break in " + playMinutes + " minutes.");
    }

    private int randomBetween(int min, int max) {
        int sanitizedMin = Math.max(1, min);
        int sanitizedMax = Math.max(sanitizedMin, max);
        return sanitizedMin + random.nextInt(sanitizedMax - sanitizedMin + 1);
    }

    private String formatRemaining(long millis) {
        long totalSeconds = Math.max(0, (millis + 999) / 1_000);
        long hours = totalSeconds / 3_600;
        long minutes = (totalSeconds % 3_600) / 60;
        long seconds = totalSeconds % 60;

        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        }

        return String.format("%02d:%02d", minutes, seconds);
    }
}
