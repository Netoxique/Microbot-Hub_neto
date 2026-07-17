package net.runelite.client.plugins.microbot.netoherbrun;

import net.runelite.client.config.*;

@ConfigGroup("neto-herbrun")
public interface NetoHerbrunConfig extends Config {

    @ConfigSection(
            name = "Instructions",
            description = "Plugin instructions",
            position = 0
    )
    String instructionsSection = "instructionsSection";

    @ConfigItem(
            keyName = "guide",
            name = "How to use",
            description = "How to use this plugin",
            position = 0,
            section = instructionsSection
    )
    default String GUIDE() {
        return "Automated Herb Runs across all patches\n\n" +
                "Withdraws:\n" +
                "• Farming tools (rake, spade, seed dibber, magic secateurs)\n" +
                "• Teleportation runes (law, air, earth, fire, water)\n" +
                "• Your selected herb seeds\n" +
                "• Your selected compost type\n" +
                "• Ectophial\n\n" +
                "Credits to liftedmango and See1Duck";
    }

    @ConfigSection(
            name = "Inventory Setup",
            description = "Setup your herb runs",
            position = 1
    )
    String inventorySetupSection = "inventorySetup";

    @ConfigItem(
            keyName = "herbSeedType",
            name = "Herb Seed Type",
            description = "Choose which herb seeds to plant",
            section = inventorySetupSection,
            position = 0
    )
    default HerbSeedType herbSeedType() {
        return HerbSeedType.RANARR;
    }

    @ConfigItem(
            keyName = "compostType",
            name = "Compost Type",
            description = "Type of compost to use (select NONE to disable composting)",
            section = inventorySetupSection,
            position = 1
    )
    default CompostType compostType() {
        return CompostType.ULTRA;
    }

    @ConfigItem(
            keyName = "allowPartialRuns",
            name = "Allow Partial Runs",
            description = "Allow herb runs with fewer seeds than patches available",
            section = inventorySetupSection,
            position = 2
    )
    default boolean allowPartialRuns() {
        return false;
    }

    @ConfigItem(
            keyName = "dropEmptyBuckets",
            name = "Drop Empty Buckets",
            description = "Drop empty buckets after applying compost to patches",
            section = inventorySetupSection,
            position = 3
    )
    default boolean dropEmptyBuckets() {
        return true;
    }

    @ConfigSection(
            name = "General Settings",
            description = "General plugin settings",
            position = 3
    )
    String settingsSection = "settings";

    @ConfigItem(
            keyName = "goToBank",
            name = "Bank After Run",
            description = "Go to closest bank after completing the herb run",
            position = 0,
            section = settingsSection
    )
    default boolean goToBank() {
        return true;
    }

    @ConfigSection(
            name = "Allotments",
            description = "Allotment patch settings",
            position = 5
    )
    String allotmentSection = "allotments";

    @ConfigItem(
            keyName = "enableAllotments",
            name = "Enable Allotments",
            description = "Plant and harvest allotment patches at each location",
            section = allotmentSection,
            position = 0
    )
    default boolean enableAllotments() {
        return false;
    }

    @ConfigItem(
            keyName = "allotmentSeedType",
            name = "Allotment Seed Type",
            description = "Choose which allotment seeds to plant (3 seeds per patch)",
            section = allotmentSection,
            position = 1
    )
    default AllotmentSeedType allotmentSeedType() {
        return AllotmentSeedType.SWEETCORN;
    }

    @ConfigSection(
            name = "Flowers",
            description = "Flower patch settings",
            position = 6
    )
    String flowerSection = "flowers";

    @ConfigItem(
            keyName = "enableFlowers",
            name = "Enable Flowers",
            description = "Plant and harvest flower patches at each location",
            section = flowerSection,
            position = 0
    )
    default boolean enableFlowers() {
        return false;
    }

    @ConfigItem(
            keyName = "flowerSeedType",
            name = "Flower Seed Type",
            description = "Choose which flower seeds to plant (White lily protects all allotments)",
            section = flowerSection,
            position = 1
    )
    default FlowerSeedType flowerSeedType() {
        return FlowerSeedType.LIMPWURT;
    }

}
