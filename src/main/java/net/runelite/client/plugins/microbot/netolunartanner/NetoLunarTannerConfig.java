package net.runelite.client.plugins.microbot.netolunartanner;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.plugins.microbot.netolunartanner.enums.Hides;

@ConfigGroup("netoLunarTanner")
public interface NetoLunarTannerConfig extends Config {
    @ConfigItem(
        keyName = "hideType",
        name = "Hide Type",
        description = "Type of hide to tan"
    )
    default Hides ITEM() {
        return Hides.GREEN_DRAGONHIDE;
    }
}
