package net.runelite.client.plugins.microbot.netodegrimer;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(NetoDegrimerConfig.GROUP)
public interface NetoDegrimerConfig extends Config
{
    String GROUP = "neto-degrimer";

    @ConfigSection(
        name = "Herb Filters",
        description = "Choose which grimy herbs to clean",
        position = 0
    )
    String herbSection = "herbSelection";

    @ConfigItem(
        keyName = "cleanGuam",
        name = "Guam leaf",
        description = "Clean grimy guam leaves.",
        position = 0,
        section = herbSection
    )
    default boolean cleanGuam()
    {
        return true;
    }

    @ConfigItem(
        keyName = "cleanMarrentill",
        name = "Marrentill",
        description = "Clean grimy marrentill.",
        position = 1,
        section = herbSection
    )
    default boolean cleanMarrentill()
    {
        return true;
    }

    @ConfigItem(
        keyName = "cleanTarromin",
        name = "Tarromin",
        description = "Clean grimy tarromin.",
        position = 2,
        section = herbSection
    )
    default boolean cleanTarromin()
    {
        return true;
    }

    @ConfigItem(
        keyName = "cleanHarralander",
        name = "Harralander",
        description = "Clean grimy harralander.",
        position = 3,
        section = herbSection
    )
    default boolean cleanHarralander()
    {
        return true;
    }

    @ConfigItem(
        keyName = "cleanRanarr",
        name = "Ranarr weed",
        description = "Clean grimy ranarr weed.",
        position = 4,
        section = herbSection
    )
    default boolean cleanRanarr()
    {
        return true;
    }

    @ConfigItem(
        keyName = "cleanToadflax",
        name = "Toadflax",
        description = "Clean grimy toadflax.",
        position = 5,
        section = herbSection
    )
    default boolean cleanToadflax()
    {
        return true;
    }

    @ConfigItem(
        keyName = "cleanIrit",
        name = "Irit leaf",
        description = "Clean grimy irit leaf.",
        position = 6,
        section = herbSection
    )
    default boolean cleanIrit()
    {
        return true;
    }

    @ConfigItem(
        keyName = "cleanAvantoe",
        name = "Avantoe",
        description = "Clean grimy avantoe.",
        position = 7,
        section = herbSection
    )
    default boolean cleanAvantoe()
    {
        return true;
    }

    @ConfigItem(
        keyName = "cleanKwuarm",
        name = "Kwuarm",
        description = "Clean grimy kwuarm.",
        position = 8,
        section = herbSection
    )
    default boolean cleanKwuarm()
    {
        return true;
    }

    @ConfigItem(
        keyName = "cleanHuasca",
        name = "Huasca",
        description = "Clean grimy huasca.",
        position = 9,
        section = herbSection
    )
    default boolean cleanHuasca()
    {
        return true;
    }

    @ConfigItem(
        keyName = "cleanSnapdragon",
        name = "Snapdragon",
        description = "Clean grimy snapdragon.",
        position = 10,
        section = herbSection
    )
    default boolean cleanSnapdragon()
    {
        return true;
    }

    @ConfigItem(
        keyName = "cleanCadantine",
        name = "Cadantine",
        description = "Clean grimy cadantine.",
        position = 11,
        section = herbSection
    )
    default boolean cleanCadantine()
    {
        return true;
    }

    @ConfigItem(
        keyName = "cleanLantadyme",
        name = "Lantadyme",
        description = "Clean grimy lantadyme.",
        position = 12,
        section = herbSection
    )
    default boolean cleanLantadyme()
    {
        return true;
    }

    @ConfigItem(
        keyName = "cleanDwarfWeed",
        name = "Dwarf weed",
        description = "Clean grimy dwarf weed.",
        position = 13,
        section = herbSection
    )
    default boolean cleanDwarfWeed()
    {
        return true;
    }

    @ConfigItem(
        keyName = "cleanTorstol",
        name = "Torstol",
        description = "Clean grimy torstol.",
        position = 14,
        section = herbSection
    )
    default boolean cleanTorstol()
    {
        return true;
    }
}
