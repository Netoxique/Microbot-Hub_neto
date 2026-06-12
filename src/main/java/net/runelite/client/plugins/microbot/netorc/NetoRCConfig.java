package net.runelite.client.plugins.microbot.netorc;

import net.runelite.client.config.*;
import net.runelite.client.plugins.microbot.netorc.enums.RuneType;
import net.runelite.client.plugins.microbot.shared.session.BreakSettings;
import net.runelite.client.plugins.microbot.shared.session.NetoWorldHopRegion;
import net.runelite.client.plugins.microbot.shared.session.RuntimeSettings;
import net.runelite.client.plugins.microbot.shared.session.WorldHopSettings;


@ConfigGroup("Frosty")
@ConfigInformation(
        "• This plugin will craft Blood and Wrath runes<br />" +
        "• <b>IF using Farming Cape, it must be used with POH</b> />" +
        "• IF making wrath runes, Myth cape must be in inventory, <b> not </b> equipped<br />" +
        "• IF using POH, ensure you have pool and fairy ring <br />" +
        "• IF not using POH, have Ardougne cloak, house tabs and Ring of Duelings(8) in bank <br />" +
        "• <b> Ensure your last destination is DLS on fairy ring </b> <br />" +
        "• Ensure you have a Colossal pouch <br />" +
        "• Ensure you have Tiara or a bound Hat of the Eye equipped <br />" +
        "• Ensure you have a RunePouch <b> with runes for NPC contact </b> for pouch repair <br />" +
        "• Start at Crafting guild or Ferox Enclave lobby <br />"

)
public interface NetoRCConfig extends Config, BreakSettings, WorldHopSettings, RuntimeSettings {
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
            keyName = "Use POH",
            name = "Use POH",
            description = "Check if you have fairy ring and pool in POH",
            position = 1,
            section = settingsSection
    )
    default boolean usePoh() {
        return false;
    }

    @ConfigItem(
            keyName = "rune type",
            name = "Rune type",
            description = "Select which type of rune to craft",
            position = 2,
            section = settingsSection
    )
    default RuneType runeType() {return RuneType.BLOOD;}

    @ConfigItem(
            keyName = "enableWorldJumping",
            name = "Enable World Jumping",
            description = "Hop to another members world after a random number of minutes.",
            position = 1,
            section = worldJumpingSection
    )
    default boolean enableWorldJumping() {
        return false;
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

    @ConfigItem(
            keyName = "showUpdateMessage",
            name = "Show Update Message",
            description = "Shows the update message in the chat console",
            position = 1,
            section = settingsSection
    )
    default boolean showUpdateMessage() {
        return true;
    }
}
