package net.runelite.client.plugins.microbot.netokarambwans;

import net.runelite.client.config.*;
import net.runelite.client.plugins.microbot.shared.session.BreakSettings;
import net.runelite.client.plugins.microbot.shared.session.NetoWorldHopRegion;
import net.runelite.client.plugins.microbot.shared.session.RuntimeSettings;
import net.runelite.client.plugins.microbot.shared.session.WorldHopSettings;

@ConfigGroup("GabulhasKarambwans")
@ConfigInformation("Automated Karambwan Fishing + Re-baiting<br/><br/>" +
        "<b>Requirements:</b><br/>" +
        "• Karambwan Vessel<br/>" +
        "• Dramen Staff<br/><br/>" +
        "• Fish Barrel (Preferrable)<br/>" +
        "• Crafting Cape (IF +85 CONS.) <br/>" +
        "• Rune Pouch (IF +85 CONS.) <br/>" +
        "• Construction Cape (Optional)<br/><br/>" +
        "<b>Setup:</b><br/>" +
        "1. Literally start with the required items in your bank.<br/>")

public interface KarambwansConfig extends Config, BreakSettings, WorldHopSettings, RuntimeSettings {
    @ConfigSection(
            name = "General",
            description = "General",
            position = 0,
            closedByDefault = false
    )
    String generalSection = "generalSection";

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
            keyName = "karambwanjiToFish",
            name = "Amount of karambwanji to fish",
            description = "The amount of karambwanji to fish when you run out of bait.",
            position = 0,
            section = generalSection
    )
    default int karambwanjiToFish() {
        return 3000;
    }

    @ConfigItem(
            keyName = "startingState",
            name = "Starting State",
            description = "Choose the initial state of the bot.",
            position = 2,
            section = generalSection
    )
    default KarambwanInfo.states STARTING_STATE() {
        return KarambwanInfo.states.FISHING;
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
        return 30;
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
        return 40;
    }

    @ConfigItem(
            keyName = "worldJumpRegion",
            name = "Region",
            description = "World region to jump to.",
            position = 4,
            section = worldJumpingSection
    )
    default NetoWorldHopRegion worldJumpRegion() {
        return NetoWorldHopRegion.UNITED_STATES_OF_AMERICA;
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
        return 70;
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

    @Override
    @ConfigItem(
            keyName = "enableRuntime",
            name = "Enable",
            description = "Enable runtime limit.",
            position = 0,
            section = "Runtime"
    )
    default boolean enableRuntime() {
        return true;
    }

    @Override
    @ConfigItem(
            keyName = "minRuntime",
            name = "Min. Runtime",
            description = "Minimum runtime before stopping the plugin (in minutes). 0 to disable.",
            position = 1,
            section = "Runtime"
    )
    default int minRuntime() {
        return 420;
    }

    @Override
    @ConfigItem(
            keyName = "maxRuntime",
            name = "Max. Runtime",
            description = "Maximum runtime before stopping the plugin (in minutes). 0 to disable.",
            position = 2,
            section = "Runtime"
    )
    default int maxRuntime() {
        return 480;
    }
}


