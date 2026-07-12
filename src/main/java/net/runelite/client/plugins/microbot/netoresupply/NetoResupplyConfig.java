package net.runelite.client.plugins.microbot.netoresupply;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup("neto-resupply")
public interface NetoResupplyConfig extends Config {
    @ConfigSection(name = "General", description = "Grand Exchange pricing and retry settings", position = 0)
    String generalSection = "generalSection";

    @ConfigSection(name = "Skill Lists", description = "Enable and configure the item lists to resupply", position = 1)
    String skillListsSection = "skillListsSection";

    @Range(min = 0, max = 1000)
    @ConfigItem(keyName = "initialMarkup", name = "Initial Markup (%)", description = "Percentage added to the guide price for the first offer", position = 0, section = generalSection)
    default int initialMarkup() { return 10; }

    @Range(min = 1, max = 3600)
    @ConfigItem(keyName = "offerTimeout", name = "Offer Timeout (seconds)", description = "Seconds to wait before aborting and reposting an unfilled offer", position = 1, section = generalSection)
    default int offerTimeoutSeconds() { return 60; }

    @Range(min = 0, max = 1000)
    @ConfigItem(keyName = "retryMarkup", name = "Retry Markup Step (%)", description = "Additional percentage points added for every repost", position = 2, section = generalSection)
    default int retryMarkupStep() { return 5; }

    @Range(min = 0, max = 100)
    @ConfigItem(keyName = "maximumReposts", name = "Maximum Reposts", description = "Maximum number of times an unfilled item is aborted and reposted", position = 3, section = generalSection)
    default int maximumReposts() { return 5; }

    @ConfigItem(keyName = "farmingEnabled", name = "Farming", description = "Purchase items from the Farming list", position = 10, section = skillListsSection)
    default boolean farmingEnabled() { return false; }
    @ConfigItem(keyName = "farmingList", name = "", description = "Comma-separated item:quantity entries", position = 11, section = skillListsSection)
    default String farmingList() { return ""; }

    @ConfigItem(keyName = "magicEnabled", name = "Magic", description = "Purchase items from the Magic list", position = 20, section = skillListsSection)
    default boolean magicEnabled() { return false; }
    @ConfigItem(keyName = "magicList", name = "", description = "Comma-separated item:quantity entries", position = 21, section = skillListsSection)
    default String magicList() { return ""; }

    @ConfigItem(keyName = "runecraftEnabled", name = "Runecraft", description = "Purchase items from the Runecraft list", position = 30, section = skillListsSection)
    default boolean runecraftEnabled() { return false; }
    @ConfigItem(keyName = "runecraftList", name = "", description = "Comma-separated item:quantity entries", position = 31, section = skillListsSection)
    default String runecraftList() { return ""; }

    @ConfigItem(keyName = "constructionEnabled", name = "Construction", description = "Purchase items from the Construction list", position = 40, section = skillListsSection)
    default boolean constructionEnabled() { return false; }
    @ConfigItem(keyName = "constructionList", name = "", description = "Comma-separated item:quantity entries", position = 41, section = skillListsSection)
    default String constructionList() { return ""; }

    @ConfigItem(keyName = "herbloreEnabled", name = "Herblore", description = "Purchase items from the Herblore list", position = 50, section = skillListsSection)
    default boolean herbloreEnabled() { return false; }
    @ConfigItem(keyName = "herbloreList", name = "", description = "Comma-separated item:quantity entries", position = 51, section = skillListsSection)
    default String herbloreList() { return ""; }

    @ConfigItem(keyName = "craftingEnabled", name = "Crafting", description = "Purchase items from the Crafting list", position = 60, section = skillListsSection)
    default boolean craftingEnabled() { return false; }
    @ConfigItem(keyName = "craftingList", name = "", description = "Comma-separated item:quantity entries", position = 61, section = skillListsSection)
    default String craftingList() { return ""; }

    @ConfigItem(keyName = "fletchingEnabled", name = "Fletching", description = "Purchase items from the Fletching list", position = 70, section = skillListsSection)
    default boolean fletchingEnabled() { return false; }
    @ConfigItem(keyName = "fletchingList", name = "", description = "Comma-separated item:quantity entries", position = 71, section = skillListsSection)
    default String fletchingList() { return ""; }

    @ConfigItem(keyName = "huntingEnabled", name = "Hunting", description = "Purchase items from the Hunting list", position = 80, section = skillListsSection)
    default boolean huntingEnabled() { return false; }
    @ConfigItem(keyName = "huntingList", name = "", description = "Comma-separated item:quantity entries", position = 81, section = skillListsSection)
    default String huntingList() { return ""; }

    @ConfigItem(keyName = "smithingEnabled", name = "Smithing", description = "Purchase items from the Smithing list", position = 90, section = skillListsSection)
    default boolean smithingEnabled() { return false; }
    @ConfigItem(keyName = "smithingList", name = "", description = "Comma-separated item:quantity entries", position = 91, section = skillListsSection)
    default String smithingList() { return ""; }

    @ConfigItem(keyName = "cookingEnabled", name = "Cooking", description = "Purchase items from the Cooking list", position = 100, section = skillListsSection)
    default boolean cookingEnabled() { return false; }
    @ConfigItem(keyName = "cookingList", name = "", description = "Comma-separated item:quantity entries", position = 101, section = skillListsSection)
    default String cookingList() { return ""; }

    @ConfigItem(keyName = "teleportsEnabled", name = "Teleports", description = "Purchase items from the Teleports list", position = 110, section = skillListsSection)
    default boolean teleportsEnabled() { return false; }
    @ConfigItem(keyName = "teleportsList", name = "", description = "Comma-separated item:quantity entries", position = 111, section = skillListsSection)
    default String teleportsList() { return ""; }
}
