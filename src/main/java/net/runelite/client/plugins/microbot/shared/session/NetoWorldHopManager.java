package net.runelite.client.plugins.microbot.shared.session;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.security.Login;
import net.runelite.http.api.worlds.WorldRegion;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Random;
import java.util.function.BooleanSupplier;

import static net.runelite.client.plugins.microbot.util.Global.sleepGaussian;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

@Singleton
public class NetoWorldHopManager {
    private final Random random = new Random();

    private WorldHopSettings settings;
    private String logPrefix = "Neto";
    private int completedTrips = 0;
    private int hopAtTrips = 0;

    @Inject
    public NetoWorldHopManager() {
    }

    public synchronized void configure(WorldHopSettings settings, String logPrefix) {
        this.settings = settings;
        this.logPrefix = logPrefix == null || logPrefix.trim().isEmpty() ? "Neto" : logPrefix.trim();
    }

    public synchronized void reset() {
        completedTrips = 0;
        scheduleNextHop();
    }

    public synchronized void recordCompletedTrip() {
        if (!isEnabled()) {
            return;
        }

        ensureHopThreshold();
        completedTrips++;
        log("world jump trips: " + completedTrips + "/" + hopAtTrips);
    }

    public synchronized boolean shouldHop() {
        return isEnabled() && hopAtTrips > 0 && completedTrips >= hopAtTrips;
    }

    public WorldHopResult tryHopIfDue(BooleanSupplier keepRunning) {
        WorldHopSettings activeSettings;
        int tripsBeforeHop;
        int maxAttempts;
        int confirmTimeoutMs;
        WorldRegion worldRegion;

        synchronized (this) {
            if (!shouldHop()) {
                return WorldHopResult.notAttempted();
            }

            activeSettings = settings;
            tripsBeforeHop = completedTrips;
            maxAttempts = Math.max(1, activeSettings.worldHopMaxAttempts());
            confirmTimeoutMs = Math.max(1, activeSettings.worldHopConfirmTimeoutMs());
            NetoWorldHopRegion hopRegion = activeSettings.worldJumpRegion();
            worldRegion = hopRegion == null ? null : hopRegion.getWorldRegion();
        }

        sleepGaussian(3500, 250);
        WorldHopResult result = attemptWorldHop(worldRegion, maxAttempts, confirmTimeoutMs, keepRunning);

        synchronized (this) {
            if (result.isConfirmed()) {
                log("confirmed world jump after " + tripsBeforeHop + " trips.");
                completedTrips = 0;
                scheduleNextHop();
            } else {
                log("world jump failed after " + maxAttempts + " attempts. Keeping trip counter at "
                        + completedTrips + "/" + hopAtTrips + ".");
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

            if (sleepUntil(() -> Microbot.isLoggedIn() && Rs2Player.getWorld() != originalWorld,
                    confirmTimeoutMs)) {
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

    private void ensureHopThreshold() {
        if (isEnabled() && hopAtTrips <= 0) {
            scheduleNextHop();
        }
    }

    private void scheduleNextHop() {
        if (!isEnabled()) {
            hopAtTrips = 0;
            return;
        }

        int minTrips = Math.max(1, settings.minTrips());
        int maxTrips = Math.max(minTrips, settings.maxTrips());
        hopAtTrips = minTrips + random.nextInt(maxTrips - minTrips + 1);
    }

    private boolean isEnabled() {
        return settings != null && settings.enableWorldJumping();
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
