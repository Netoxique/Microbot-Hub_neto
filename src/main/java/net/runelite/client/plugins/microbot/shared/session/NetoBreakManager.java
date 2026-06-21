package net.runelite.client.plugins.microbot.shared.session;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.security.Login;
import net.runelite.client.ui.ClientUI;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.*;
import java.util.Random;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

@Singleton
public class NetoBreakManager {
    private static final long MINUTE_MILLIS = 60_000L;
    private static final int LOGIN_TIMEOUT_MS = 15_000;
    private static final int LOGOUT_RETRY_DELAY_MS = 10_000;
    private static final int LOGIN_RETRY_DELAY_MS = 10_000;

    private final Random random = new Random();

    private BreakSettings settings;
    private String logPrefix = "Neto";
    private long nextBreakAtMillis = 0;
    private long breakEndsAtMillis = 0;
    private long nextLogoutAttemptAtMillis = 0;
    private long nextLoginAttemptAtMillis = 0;
    private boolean breakActive = false;
    private int breakWorld = -1;
    private int totalBreaks = 0;
    private String originalTitle = "";

    @Inject
    public NetoBreakManager() {
    }

    public synchronized void configure(BreakSettings settings, String logPrefix) {
        this.settings = settings;
        this.logPrefix = logPrefix == null || logPrefix.trim().isEmpty() ? "Neto" : logPrefix.trim();
        if (this.originalTitle == null || this.originalTitle.isEmpty()) {
            this.originalTitle = ClientUI.getFrame().getTitle();
        }
    }

    public synchronized void reset() {
        nextBreakAtMillis = 0;
        breakEndsAtMillis = 0;
        nextLogoutAttemptAtMillis = 0;
        nextLoginAttemptAtMillis = 0;
        breakActive = false;
        breakWorld = -1;
        totalBreaks = 0;
        restoreTitle();
    }

    public boolean updateBreakState() {
        long now = System.currentTimeMillis();
        boolean isActive;
        long endsAt;
        long nextLogout;
        long nextLogin;
        int world;

        synchronized (this) {
            if (!breakActive) {
                ensurePlayTimer();
                return false;
            }
            isActive = breakActive;
            endsAt = breakEndsAtMillis;
            nextLogout = nextLogoutAttemptAtMillis;
            nextLogin = nextLoginAttemptAtMillis;
            world = breakWorld;
        }

        updateTitle();

        if (now < endsAt) {
            if (Microbot.isLoggedIn() && now >= nextLogout) {
                requestLogout();
            }
            return true;
        }

        if (Microbot.isLoggedIn()) {
            log("break window ended while still logged in. Scheduling next playtime.");
            finishBreak();
            return false;
        }

        if (now >= nextLogin) {
            synchronized (this) {
                nextLoginAttemptAtMillis = now + LOGIN_RETRY_DELAY_MS;
            }
            log("break complete, logging back into world " + world + ".");
            new Login(world);
            if (sleepUntil(() -> {
                Microbot.getBlockingEventManager().shouldBlockAndProcess();
                return Microbot.isLoggedIn();
            }, LOGIN_TIMEOUT_MS)) {
                finishBreak();
            }
        }

        return true;
    }

    public boolean tryStartBreakAtSafePoint() {
        int durationMinutes;
        int world;
        long now = System.currentTimeMillis();

        synchronized (this) {
            ensurePlayTimer();
            if (!isEnabled() || breakActive || nextBreakAtMillis <= 0 || now < nextBreakAtMillis) {
                return false;
            }

            durationMinutes = randomBetween(settings.minBreak(), settings.maxBreak());
            world = Rs2Player.getWorld();
            breakWorld = world;
            breakEndsAtMillis = now + (long) durationMinutes * MINUTE_MILLIS;
            nextLogoutAttemptAtMillis = 0;
            nextLoginAttemptAtMillis = 0;
            breakActive = true;
            totalBreaks++;
        }

        log("starting logout break for " + durationMinutes + " minutes on world " + world + ".");
        updateTitle();
        requestLogout();
        return true;
    }

    public synchronized boolean shouldStartBreakAtSafePoint() {
        ensurePlayTimer();
        long now = System.currentTimeMillis();
        return isEnabled() && !breakActive && nextBreakAtMillis > 0 && now >= nextBreakAtMillis;
    }

    public synchronized String getBreakInDisplay() {
        ensurePlayTimer();

        if (breakActive) {
            return "On break " + formatRemaining(breakEndsAtMillis - System.currentTimeMillis());
        }

        if (!isEnabled()) {
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
        log("break finished. Scheduling next playtime.");
        synchronized (this) {
            breakActive = false;
            breakEndsAtMillis = 0;
            breakWorld = -1;
            nextLogoutAttemptAtMillis = 0;
            nextLoginAttemptAtMillis = 0;
            scheduleNextPlayTimer();
        }
        restoreTitle();
    }

    private void requestLogout() {
        synchronized (this) {
            nextLogoutAttemptAtMillis = System.currentTimeMillis() + LOGOUT_RETRY_DELAY_MS;
        }

        try {
            Rs2Player.logout();
        } catch (Exception ex) {
            log("logout request failed, retrying soon: " + ex.getMessage());
        }
    }

    private void ensurePlayTimer() {
        if (!isEnabled()) {
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
        if (!isEnabled()) {
            nextBreakAtMillis = 0;
            return;
        }

        int playMinutes = randomBetween(settings.minPlaytime(), settings.maxPlaytime());
        nextBreakAtMillis = System.currentTimeMillis() + playMinutes * MINUTE_MILLIS;
        log("next logout break in " + playMinutes + " minutes.");
    }

    private boolean isEnabled() {
        return settings != null && settings.enableBreaks();
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

    private void log(String message) {
        Microbot.log(logPrefix + " " + message);
    }

    private void updateTitle() {
        String remaining;
        synchronized (this) {
            if (!breakActive) return;
            remaining = formatRemaining(breakEndsAtMillis - System.currentTimeMillis());
        }
        String title = String.format("[BREAK: %s] %s", remaining, originalTitle);
        SwingUtilities.invokeLater(() -> {
            try {
                ClientUI.getFrame().setTitle(title);
            } catch (Exception ignored) {
            }
        });
    }

    private void restoreTitle() {
        if (originalTitle == null || originalTitle.isEmpty()) return;
        SwingUtilities.invokeLater(() -> {
            try {
                ClientUI.getFrame().setTitle(originalTitle);
            } catch (Exception ignored) {
            }
        });
    }
}
