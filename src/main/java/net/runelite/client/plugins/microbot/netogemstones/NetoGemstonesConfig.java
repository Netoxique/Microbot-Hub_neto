package net.runelite.client.plugins.microbot.netogemstones;

import net.runelite.client.config.Config;
import net.runelite.http.api.worlds.WorldRegion;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("neto-gemstones")
public interface NetoGemstonesConfig extends Config {
    @ConfigSection(
            name = "General",
            description = "General settings",
            position = 0
    )
    String generalSettings = "generalSettings";

    @ConfigItem(
            keyName = "guide",
            name = "How to use",
            description = "How to use this plugin",
            position = 0,
            section = generalSettings
    )
    default String GUIDE() {
        return "Start near a gem rock with a pickaxe in your inventory or equipped. Gems bag and charged Amulet of Glory very recommended.";
    }

    @ConfigItem(
            keyName = "hopOnPlayerDetect",
            name = "Hop on Player Detect",
            description = "Hops to another world if a player is detected nearby.",
            position = 1,
            section = generalSettings
    )
    default boolean hopOnPlayerDetect() {
        return false;
    }

    @ConfigItem(
            keyName = "distance",
            name = "Distance to hop",
            description = "The distance in tiles to check for other players.",
            position = 2,
            section = generalSettings
    )
    default int distanceToHop() {
        return 10;
    }

    @ConfigItem(
            keyName = "worldRegion",
            name = "World Region",
            description = "The region to hop to.",
            position = 3,
            section = generalSettings
    )
    default WorldRegion worldRegion() {
        return WorldRegion.UNITED_STATES_OF_AMERICA;
    }
}
