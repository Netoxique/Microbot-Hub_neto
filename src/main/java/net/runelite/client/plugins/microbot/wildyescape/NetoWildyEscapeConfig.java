package net.runelite.client.plugins.microbot.wildyescape;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup("netowildyescape")
public interface NetoWildyEscapeConfig extends Config
{
    @ConfigItem(
            keyName = "escapeOnHealth",
            name = "Escape at health %",
            description = "Escape when health falls below the configured percentage"
    )
    default boolean escapeOnHealth()
    {
        return false;
    }

    @ConfigItem(
            keyName = "healthPercent",
            name = "Health %",
            description = "Health percentage threshold to trigger escape"
    )
    @Range(min = 20, max = 80)
    default int healthPercent()
    {
        return 40;
    }
}
