package net.runelite.client.plugins.microbot.netosuperglassmake;

import net.runelite.client.config.*;

@ConfigGroup("NetoSuperglassMake")
@ConfigInformation(
        "Just make sure to have Seaweed and Buckets of sand in your bank."
)
public interface NetoSuperglassMakeConfig extends Config {

    @ConfigSection(
            name = "Settings",
            description = "Settings",
            position = 0,
            closedByDefault = false
    )
    String settingsSection = "settings";

    @ConfigItem(
            keyName = "Seaweed type",
            name = "seaweed type",
            description = "Choose the type of seaweed",
            position = 0,
            section = settingsSection
    )
    default NetoSuperglassMakeInfo.items ITEM()
    {
        return NetoSuperglassMakeInfo.items.GiantSeaweed;
    }

    @ConfigItem(
            keyName = "pickUpGlass",
            name = "Pick up glass",
            description = "Pick up glass from the floor",
            position = 1,
            section = settingsSection
    )
    default boolean pickUpGlass()
    {
        return false;
    }
}
