package net.runelite.client.plugins.microbot.shared.session;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.security.Login;
import net.runelite.http.api.worlds.WorldRegion;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static net.runelite.client.plugins.microbot.util.Global.sleepGaussian;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

@Singleton
public class NetoWorldHopManager {
    private final Random random = new Random();

    private WorldHopSettings settings;
    private String logPrefix = "Neto";
    private long nextHopAtMillis = 0;
    private int scheduledDelayMinutes = 0;

    @Inject
    public NetoWorldHopManager() {
    }

    public synchronized void configure(WorldHopSettings settings, String logPrefix) {
        this.settings = settings;
        this.logPrefix = logPrefix == null || logPrefix.trim().isEmpty() ? "Neto" : logPrefix.trim();
    }

    public synchronized void reset() {
        scheduleNextHop();
    }

    public synchronized boolean shouldHop() {
        ensureHopTime();
        return isEnabled() && nextHopAtMillis > 0 && System.currentTimeMillis() >= nextHopAtMillis;
    }

    public synchronized String getWorldHopDisplay() {
        if (!isEnabled()) {
            return "Disabled";
        }

        ensureHopTime();
        long remainingMillis = nextHopAtMillis - System.currentTimeMillis();
        if (remainingMillis <= 0) {
            return "Due";
        }

        return formatDuration(remainingMillis) + " / " + scheduledDelayMinutes + "m";
    }

    public WorldHopResult tryHopIfDue(BooleanSupplier keepRunning) {
        WorldHopSettings activeSettings;
        int delayBeforeHop;
        int maxAttempts;
        int confirmTimeoutMs;
        WorldRegion worldRegion;

        synchronized (this) {
            if (!shouldHop()) {
                return WorldHopResult.notAttempted();
            }

            activeSettings = settings;
            delayBeforeHop = scheduledDelayMinutes;
            maxAttempts = Math.max(1, activeSettings.worldHopMaxAttempts());
            confirmTimeoutMs = Math.max(1, activeSettings.worldHopConfirmTimeoutMs());
            NetoWorldHopRegion hopRegion = activeSettings.worldJumpRegion();
            worldRegion = hopRegion == null ? null : hopRegion.getWorldRegion();
        }

        sleepGaussian(3500, 250);
        WorldHopResult result = attemptWorldHop(worldRegion, maxAttempts, confirmTimeoutMs, keepRunning);

        synchronized (this) {
            if (result.isConfirmed()) {
                log("confirmed world jump after " + delayBeforeHop + " minute timer.");
                scheduleNextHop();
            } else {
                log("world jump failed after " + maxAttempts + " attempts. Keeping timer due.");
            }
        }

        return result;
    }

    private WorldHopResult attemptWorldHop(
            WorldRegion worldRegion,
            int maxAttempts,
            int confirmTimeoutMs,
            BooleanSupplier keepRunning) {
        int originalWorld = Rs2Player.getWorld();
        int lastTargetWorld = -1;

        for (int attempt = 1; attempt <= maxAttempts && keepRunning.getAsBoolean(); attempt++) {
            if (Microbot.isLoggedIn() && Rs2Player.getWorld() != originalWorld) {
                int finalWorld = Rs2Player.getWorld();
                log("world jump confirmed: " + originalWorld + " -> " + finalWorld + ".");
                return WorldHopResult.confirmed(originalWorld, lastTargetWorld, finalWorld);
            }

            int currentWorld = Rs2Player.getWorld();
            int targetWorld = getRandomMemberWorld(worldRegion, currentWorld, maxAttempts);
            lastTargetWorld = targetWorld;

            log("world jump attempt " + attempt + "/" + maxAttempts
                    + ": requesting members world " + targetWorld + " from world " + originalWorld + ".");

            try {
                Microbot.hopToWorld(targetWorld);
            } catch (Exception ex) {
                log("world jump attempt failed to start: " + ex.getMessage());
            }

            long startTime = System.currentTimeMillis();
            boolean confirmed = false;

            while (keepRunning.getAsBoolean() && (System.currentTimeMillis() - startTime) < confirmTimeoutMs) {
                if (Microbot.isLoggedIn() && Rs2Player.getWorld() != originalWorld) {
                    confirmed = true;
                    break;
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    break;
                }
            }

            if (confirmed) {
                int finalWorld = Rs2Player.getWorld();
                log("world jump confirmed: " + originalWorld + " -> " + finalWorld + ".");
                return WorldHopResult.confirmed(originalWorld, targetWorld, finalWorld);
            }

            log("world jump attempt " + attempt + "/" + maxAttempts + " did not confirm a world change.");
        }

        return WorldHopResult.failed(originalWorld, lastTargetWorld, Rs2Player.getWorld());
    }

    private int getRandomMemberWorld(WorldRegion worldRegion, int currentWorld, int maxAttempts) {
        int targetWorld = Login.getRandomWorld(true, worldRegion);

        for (int reroll = 0; reroll < maxAttempts && targetWorld == currentWorld; reroll++) {
            targetWorld = Login.getRandomWorld(true, worldRegion);
        }

        return targetWorld;
    }

    private void ensureHopTime() {
        if (isEnabled() && nextHopAtMillis <= 0) {
            scheduleNextHop();
        }
    }

    private void scheduleNextHop() {
        if (!isEnabled()) {
            nextHopAtMillis = 0;
            scheduledDelayMinutes = 0;
            return;
        }

        int minMinutes = Math.max(1, settings.minMinutes());
        int maxMinutes = Math.max(minMinutes, settings.maxMinutes());
        scheduledDelayMinutes = minMinutes + random.nextInt(maxMinutes - minMinutes + 1);
        nextHopAtMillis = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(scheduledDelayMinutes);
        log("next world jump scheduled in " + scheduledDelayMinutes + " minutes.");
    }

    private boolean isEnabled() {
        return settings != null && settings.enableWorldJumping();
    }

    private String formatDuration(long millis) {
        long totalSeconds = Math.max(0, TimeUnit.MILLISECONDS.toSeconds(millis));
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        }

        return String.format("%02d:%02d", minutes, seconds);
    }

    private void log(String message) {
        Microbot.log(logPrefix + " " + message);
    }

    public static final class WorldHopResult {
        private final boolean attempted;
        private final boolean confirmed;
        private final int originalWorld;
        private final int targetWorld;
        private final int finalWorld;

        private WorldHopResult(boolean attempted, boolean confirmed, int originalWorld, int targetWorld, int finalWorld) {
            this.attempted = attempted;
            this.confirmed = confirmed;
            this.originalWorld = originalWorld;
            this.targetWorld = targetWorld;
            this.finalWorld = finalWorld;
        }

        public static WorldHopResult notAttempted() {
            return new WorldHopResult(false, false, -1, -1, -1);
        }

        public static WorldHopResult confirmed(int originalWorld, int targetWorld, int finalWorld) {
            return new WorldHopResult(true, true, originalWorld, targetWorld, finalWorld);
        }

        public static WorldHopResult failed(int originalWorld, int targetWorld, int finalWorld) {
            return new WorldHopResult(true, false, originalWorld, targetWorld, finalWorld);
        }

        public boolean isAttempted() {
            return attempted;
        }

        public boolean isConfirmed() {
            return confirmed;
        }

        public int getOriginalWorld() {
            return originalWorld;
        }

        public int getTargetWorld() {
            return targetWorld;
        }

        public int getFinalWorld() {
            return finalWorld;
        }
    }
}
