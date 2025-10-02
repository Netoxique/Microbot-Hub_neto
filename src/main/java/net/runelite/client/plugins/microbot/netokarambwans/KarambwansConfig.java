package net.runelite.client.plugins.microbot.netokarambwans;

import net.runelite.client.config.*;

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

public interface KarambwansConfig extends Config {
    @ConfigSection(
            name = "General",
            description = "General",
            position = 0,
            closedByDefault = false
    )
    String generalSection = "generalSection";

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
}


