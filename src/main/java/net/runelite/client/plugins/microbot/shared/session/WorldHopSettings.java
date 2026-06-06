package net.runelite.client.plugins.microbot.shared.session;

public interface WorldHopSettings {
    boolean enableWorldJumping();

    int minTrips();

    int maxTrips();

    NetoWorldHopRegion worldJumpRegion();

    default int worldHopMaxAttempts() {
        return 3;
    }

    default int worldHopConfirmTimeoutMs() {
        return 15_000;
    }
}
