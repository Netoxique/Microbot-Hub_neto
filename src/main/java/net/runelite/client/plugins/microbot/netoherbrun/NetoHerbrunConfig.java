package net.runelite.client.plugins.microbot.netoherbrun;

import net.runelite.client.config.*;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;

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
                "Two Setup Options:\n" +
                "1. Inventory Setup: Use your custom inventory configuration\n" +
                "2. Auto Banking: Let the plugin handle everything!\n\n" +
                "Auto Banking withdraws:\n" +
                "• Farming tools (rake, spade, seed dibber, magic secateurs)\n" +
                "• Teleportation runes (law, air, earth, fire, water)\n" +
                "• Your selected herb seeds\n" +
                "• Your selected compost type\n" +
                "• Ectophial (if Morytania is enabled)\n\n" +
                "Credits to liftedmango and See1Duck";
    }

    @ConfigSection(
            name = "Inventory Setup Method",
            description = "Choose between inventory setup or auto banking",
            position = 1
    )
    String inventorySection = "inventory";

    @ConfigItem(
            keyName = "useInventorySetup",
            name = "Use Inventory Setup",
            description = "Enable to use RuneLite inventory setups | Disable for automatic banking",
            section = inventorySection,
            position = 0
    )
    default boolean useInventorySetup() {
        return false;
    }

    @ConfigItem(
            keyName = "inventorySetup",
            name = "Inventory Setup Name",
            description = "Select your pre-configured inventory setup",
            section = inventorySection,
            position = 1
    )
    default InventorySetup inventorySetup() {
        return null;
    }

    @ConfigSection(
            name = "Auto Banking Settings",
            description = "Configure automatic banking options",
            position = 2
    )
    String autoSection = "autobanking";

    @ConfigItem(
            keyName = "herbSeedType",
            name = "Herb Seed Type",
            description = "Choose which herb seeds to plant",
            section = autoSection,
            position = 0
    )
    default HerbSeedType herbSeedType() {
        return HerbSeedType.RANARR;
    }

    @ConfigItem(
            keyName = "compostType",
            name = "Compost Type",
            description = "Type of compost to use (select NONE to disable composting)",
            section = autoSection,
            position = 1
    )
    default CompostType compostType() {
        return CompostType.ULTRA;
    }

    @ConfigItem(
            keyName = "allowPartialRuns",
            name = "Allow Partial Runs",
            description = "Allow herb runs with fewer seeds than patches available",
            section = autoSection,
            position = 2
    )
    default boolean allowPartialRuns() {
        return false;
    }

    @ConfigItem(
            keyName = "dropEmptyBuckets",
            name = "Drop Empty Buckets",
            description = "Drop empty buckets after applying compost to patches",
            section = autoSection,
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

    @ConfigItem(
            keyName = "enableTrollheim",
            name = "Enable Trollheim Patch",
            description = "Enable Trollheim patch in herb run",
            position = 1,
            section = locationSection
    )
    default boolean enableTrollheim() {
        return true;
    }

    @ConfigItem(
            keyName = "enableCatherby",
            name = "Enable Catherby Patch",
            description = "Enable Catherby patch in herb run",
            position = 2,
            section = locationSection
    )
    default boolean enableCatherby() {
        return true;
    }

    @ConfigItem(
            keyName = "enableMorytania",
            name = "Enable Morytania Patch",
            description = "Enable Morytania patch in herb run",
            position = 3,
            section = locationSection
    )
    default boolean enableMorytania() {
        return true;
    }

    @ConfigItem(
            keyName = "enableVarlamore",
            name = "Enable Varlamore Patch",
            description = "Enable Varlamore patch in herb run",
            position = 4,
            section = locationSection
    )
    default boolean enableVarlamore() {
        return true;
    }

    @ConfigItem(
            keyName = "enableHosidius",
            name = "Enable Hosidius Patch",
            description = "Enable Hosidius patch in herb run",
            position = 5,
            section = locationSection
    )
    default boolean enableHosidius() {
        return true;
    }

    @ConfigItem(
            keyName = "enableArdougne",
            name = "Enable Ardougne Patch",
            description = "Enable Ardougne patch in herb run",
            position = 6,
            section = locationSection
    )
    default boolean enableArdougne() {
        return true;
    }

    @ConfigItem(
            keyName = "enableFalador",
            name = "Enable Falador Patch",
            description = "Enable Falador patch in herb run",
            position = 7,
            section = locationSection
    )
    default boolean enableFalador() {
        return true;
    }

    @ConfigItem(
            keyName = "enableWeiss",
            name = "Enable Weiss Patch",
            description = "Enable Weiss patch in herb run",
            position = 8,
            section = locationSection
    )
    default boolean enableWeiss() {
        return true;
    }

    @ConfigItem(
            keyName = "enableGuild",
            name = "Enable Farming Guild Patch",
            description = "Enable Farming Guild patch in herb run",
            position = 9,
            section = locationSection
    )
    default boolean enableGuild() {
        return true;
    }

    @ConfigSection(
            name = "Location toggles",
            description = "Location toggles",
            position = 4
    )
    String locationSection = "Location";

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
