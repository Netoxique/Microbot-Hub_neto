package net.runelite.client.plugins.microbot.netosuperglassmake;

import net.runelite.client.config.*;

@ConfigGroup("NetoSuperglassMake")
@ConfigInformation(
        "Just make sure to have Seaweed and Buckets of sand in your bank."
)
public interface NetoSuperglassMakeConfig extends Config {

    @ConfigSection(
            name = "General",
            description = "General",
            position = 0,
            closedByDefault = false
    )
    String generalSection = "general";

    @ConfigItem(
            keyName = "Seaweed type",
            name = "seaweed type",
            description = "Choose the type of seaweed",
            position = 0,
            section = generalSection
    )
    default NetoSuperglassMakeInfo.items ITEM()
    {
        return NetoSuperglassMakeInfo.items.GiantSeaweed;
    }
}
