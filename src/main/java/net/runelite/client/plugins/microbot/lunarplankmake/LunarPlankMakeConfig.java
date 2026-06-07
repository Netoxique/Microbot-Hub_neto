package net.runelite.client.plugins.microbot.lunarplankmake;

import net.runelite.client.config.*;
import net.runelite.client.plugins.microbot.lunarplankmake.enums.Logs;
import net.runelite.client.plugins.microbot.shared.session.BreakSettings;
import net.runelite.client.plugins.microbot.shared.session.NetoWorldHopRegion;
import net.runelite.client.plugins.microbot.shared.session.RuntimeSettings;
import net.runelite.client.plugins.microbot.shared.session.WorldHopSettings;

@ConfigGroup("plankMake")
public interface LunarPlankMakeConfig extends Config, BreakSettings, WorldHopSettings, RuntimeSettings {
    String GROUP = "Plank Make";

    @ConfigSection(
            name = "General",
            description = "General",
            position = 0,
            closedByDefault = false
    )
    String generalSection = "general";

    @ConfigSection(
            name = "World Jumping",
            description = "World jumping settings",
            position = 1
    )
    String worldJumpingSection = "World Jumping";

    @ConfigSection(
            name = "Breaks",
            description = "Break settings",
            position = 2
    )
    String breaksSection = "Breaks";

    @ConfigSection(
            name = "Runtime",
            description = "Runtime disable settings",
            position = 3
    )
    String runtimeSection = "Runtime";

    @ConfigItem(
            keyName = "guide",
            name = "How to use",
            description = "How to use this plugin",
            position = 0,
            section = generalSection
    )
    default String GUIDE() {
        return "To initiate the process, please begin at a bank with Astral, Earth, nature " +
                "runes and coins in your inventory. If you are using the Mud staff " +
                "equip that and ensure that you have previously pre-cast the " +
                "Plank Make spell on the desired log and acknowledge the " +
                "prompt to avoid any further notifications. With these steps complete, " +
                "you should be ready to proceed. Lazy mode casts once and uses one log; " +
                "the game processes the rest of that log type in one chain.";
    }

    @ConfigItem(
            keyName = "logType",
            name = "Log Type",
            description = "Type of plank to make",
            position = 1,
            section = generalSection
    )
    default Logs ITEM() {
        return Logs.LOGS;
    }

    @ConfigItem(
            keyName = "useSawmillVouchers",
            name = "Use Sawmill Vouchers",
            description = "Uses vouchers for double planks (12 logs -> 24 planks)",
            position = 2,
            section = generalSection
    )
    default boolean useSawmillVouchers() {
        return false;
    }

    @ConfigItem(
            keyName = "lazyMode",
            name = "Lazy mode",
            description = "Cast Plank Make once, use one log, then wait until every log in inventory is converted (no per-log cast loop)",
            position = 3,
            section = generalSection
    )
    default boolean lazyMode() {
        return false;
    }

    @ConfigItem(
            keyName = "includeEarthRuneCost",
            name = "Include Earth rune cost",
            description = "Count 15 Earth runes per plank in profit (turn off if using mud/earth staff)",
            position = 4,
            section = generalSection
    )
    default boolean includeEarthRuneCost() {
        return false;
    }

    @ConfigItem(
            keyName = "useSetDelay",
            name = "Use Set Delay",
            description = "Enable to use a set delay between actions",
            position = 5,
            section = generalSection
    )
    default boolean useSetDelay() {
        return false;
    }

    @ConfigItem(
            keyName = "setDelay",
            name = "Set Delay (ms)",
            description = "The fixed delay in milliseconds between actions",
            position = 6,
            section = generalSection
    )
    default int setDelay() {
        return 500; // Default to 500 milliseconds
    }

    @ConfigItem(
            keyName = "useRandomDelay",
            name = "Use Random Delay",
            description = "Enable to use a random delay between actions",
            position = 7,
            section = generalSection
    )
    default boolean useRandomDelay() {
        return false;
    }

    @ConfigItem(
            keyName = "maxRandomDelay",
            name = "Maximum Random Delay (ms)",
            description = "The maximum random delay in milliseconds between actions",
            position = 8,
            section = generalSection
    )
    default int maxRandomDelay() {
        return 1000; // Default to 1000 milliseconds
    }

    @ConfigItem(
            keyName = "enableWorldJumping",
            name = "Enable World Jumping",
            description = "Hop to another members world after a random number of completed inventories.",
            position = 1,
            section = worldJumpingSection
    )
    default boolean enableWorldJumping() {
        return false;
    }

    @Range(min = 1)
    @ConfigItem(
            keyName = "minTrips",
            name = "Min. Inventories",
            description = "Minimum completed inventories before world jumping.",
            position = 2,
            section = worldJumpingSection
    )
    default int minTrips() {
        return 25;
    }

    @Range(min = 1)
    @ConfigItem(
            keyName = "maxTrips",
            name = "Max. Inventories",
            description = "Maximum completed inventories before world jumping.",
            position = 3,
            section = worldJumpingSection
    )
    default int maxTrips() {
        return 30;
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
        return false;
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