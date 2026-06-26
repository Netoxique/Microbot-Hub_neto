package net.runelite.client.plugins.microbot.netolunartanner;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("netoLunarTanner")
public interface NetoLunarTannerConfig extends Config {
    @ConfigItem(
        keyName = "hidePriority",
        name = "Hide Priority",
        description = "Comma-separated list of hides in priority order (e.g. green, blue, red, black)"
    )
    default String hidePriority() {
        return "green, blue, red, black";
    }
}
