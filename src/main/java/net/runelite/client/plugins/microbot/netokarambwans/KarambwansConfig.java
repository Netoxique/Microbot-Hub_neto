package net.runelite.client.plugins.microbot.netokarambwans;

import net.runelite.client.config.*;

@ConfigGroup("GabulhasKarambwans")
@ConfigInformation(
        "<ol>" +
                "<li>Configure the fairy rings to DKP (last destination must be DKP)</li>" +
                "<li>Make sure to have karambwan vessel and raw karambwanji in your inventory</li>" +
                "<li>Start the script next to the karambwan fishing spot</li>" +
                "</ol>"
)

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
            position = 1,
            section = generalSection
    )
    default KarambwanInfo.states STARTING_STATE() {
        return KarambwanInfo.states.FISHING;
    }
}


