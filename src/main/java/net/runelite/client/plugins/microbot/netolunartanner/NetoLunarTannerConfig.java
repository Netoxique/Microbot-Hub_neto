package net.runelite.client.plugins.microbot.netolunartanner;

import net.runelite.client.config.*;
import net.runelite.client.plugins.microbot.shared.session.BreakSettings;
import net.runelite.client.plugins.microbot.shared.session.NetoWorldHopRegion;
import net.runelite.client.plugins.microbot.shared.session.RuntimeSettings;
import net.runelite.client.plugins.microbot.shared.session.WorldHopSettings;

@ConfigGroup("netoLunarTanner")
public interface NetoLunarTannerConfig extends Config, BreakSettings, WorldHopSettings, RuntimeSettings {
    @ConfigSection(
            name = "Settings",
            description = "Settings",
            position = 2
    )
    String settingsSection = "Settings";

    @ConfigSection(
            name = "World Jumping",
            description = "World jumping settings",
            position = 3
    )
    String worldJumpingSection = "World Jumping";

    @ConfigSection(
            name = "Breaks",
            description = "Break settings",
            position = 4
    )
    String breaksSection = "Breaks";

    @ConfigSection(
            name = "Runtime",
            description = "Runtime disable settings",
            position = 5
    )
    String runtimeSection = "Runtime";

    @ConfigItem(
            keyName = "hidePriority",
            name = "Hide Priority",
            description = "Comma-separated list of hides in priority order (e.g. green, blue, red, black)",
            position = 1,
            section = settingsSection
    )
    default String hidePriority() {
        return "green, blue, red, black";
    }

    @ConfigItem(
            keyName = "enableWorldJumping",
            name = "Enable World Jumping",
            description = "Hop to another members world after a random number of minutes.",
            position = 1,
            section = worldJumpingSection
    )
    default boolean enableWorldJumping() {
        return true;
    }

    @Range(min = 1)
    @ConfigItem(
            keyName = "minMinutes",
            name = "Min. Minutes",
            description = "Minimum minutes before world jumping.",
            position = 2,
            section = worldJumpingSection
    )
    default int minMinutes() {
        return 10;
    }

    @Range(min = 1)
    @ConfigItem(
            keyName = "maxMinutes",
            name = "Max. Minutes",
            description = "Maximum minutes before world jumping.",
            position = 3,
            section = worldJumpingSection
    )
    default int maxMinutes() {
        return 15;
    }

    @ConfigItem(
            keyName = "worldJumpRegion",
            name = "Region",
            description = "World region to jump to.",
            position = 4,
            section = worldJumpingSection
    )
    default NetoWorldHopRegion worldJumpRegion() {
        return NetoWorldHopRegion.ALL;
    }

    @ConfigItem(
            keyName = "enableBreaks",
            name = "Enable",
            description = "Enable logout breaks.",
            position = 1,
            section = breaksSection
    )
    default boolean enableBreaks() {
        return true;
    }

    @Range(min = 1)
    @ConfigItem(
            keyName = "minPlaytime",
            name = "Min. Playtime",
            description = "Minimum playtime before a break, in minutes.",
            position = 2,
            section = breaksSection
    )
    default int minPlaytime() {
        return 60;
    }

    @Range(min = 1)
    @ConfigItem(
            keyName = "maxPlaytime",
            name = "Max. Playtime",
            description = "Maximum playtime before a break, in minutes.",
            position = 3,
            section = breaksSection
    )
    default int maxPlaytime() {
        return 90;
    }

    @Range(min = 1)
    @ConfigItem(
            keyName = "minBreak",
            name = "Min. Break",
            description = "Minimum break duration, in minutes.",
            position = 4,
            section = breaksSection
    )
    default int minBreak() {
        return 10;
    }

    @Range(min = 1)
    @ConfigItem(
            keyName = "maxBreak",
            name = "Max. Break",
            description = "Maximum break duration, in minutes.",
            position = 5,
            section = breaksSection
    )
    default int maxBreak() {
        return 15;
    }

    @ConfigItem(
            keyName = "enableRuntime",
            name = "Enable",
            description = "Enable runtime limit.",
            position = 0,
            section = runtimeSection
    )
    default boolean enableRuntime() {
        return true;
    }

    @ConfigItem(
            keyName = "minRuntime",
            name = "Min. Runtime",
            description = "Minimum runtime before stopping the plugin (in minutes). 0 to disable.",
            position = 1,
            section = runtimeSection
    )
    default int minRuntime() {
        return 360;
    }

    @ConfigItem(
            keyName = "maxRuntime",
            name = "Max. Runtime",
            description = "Maximum runtime before stopping the plugin (in minutes). 0 to disable.",
            position = 2,
            section = runtimeSection
    )
    default int maxRuntime() {
        return 480;
    }
}
