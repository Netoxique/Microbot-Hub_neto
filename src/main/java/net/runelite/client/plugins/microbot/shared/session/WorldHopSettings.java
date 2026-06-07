package net.runelite.client.plugins.microbot.shared.session;

import net.runelite.client.config.ConfigItem;

public interface WorldHopSettings {
    boolean enableWorldJumping();

    int minTrips();

    int maxTrips();

    NetoWorldHopRegion worldJumpRegion();

    @ConfigItem(
            keyName = "worldHopMaxAttempts",
            name = "World Hop Max Attempts",
            description = "Maximum number of attempts to hop to a new world.",
            hidden = true
    )
    default int worldHopMaxAttempts() {
        return 3;
    }

    @ConfigItem(
            keyName = "worldHopConfirmTimeoutMs",
            name = "World Hop Confirm Timeout",
            description = "Timeout in milliseconds to wait for a world hop to be confirmed.",
            hidden = true
    )
    default int worldHopConfirmTimeoutMs() {
        return 5_000;
    }
}
