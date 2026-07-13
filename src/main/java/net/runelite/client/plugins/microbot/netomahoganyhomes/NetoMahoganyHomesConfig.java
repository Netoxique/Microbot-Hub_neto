package net.runelite.client.plugins.microbot.netomahoganyhomes;

import net.runelite.client.config.*;

import java.awt.*;

@ConfigGroup(NetoMahoganyHomesConfig.GROUP_NAME)
public interface NetoMahoganyHomesConfig extends Config
{
    String GROUP_NAME = "NetoMahoganyHomesBot";
    String HOME_KEY = "currentHome";
    String TIER_KEY = "currentTier";
    String WORLD_MAP_KEY = "worldMapIcon";
    String HINT_ARROW_KEY = "displayHintArrows";

    @ConfigItem(
            keyName = "instructions",
            name = "Instructions",
            description = "Editable instructions. Click reset to restore default.",
            position = 0
    )
    default String instructions()
    {
        return "Contracts: Select your desired contract BEFORE starting.";
    }

    @ConfigItem(
            keyName = WORLD_MAP_KEY,
            name = "Display World Map Icon",
            description = "Configures whether an icon will be displayed on the world map showing where to go for your current contract",
            position = 1,
            hidden = true
    )
    default boolean worldMapIcon()
    {
        return true;
    }

    // Contract tier selection
    @ConfigItem(
            keyName = TIER_KEY,
            name = "Contract Tier",
            description = "Configures the tier of contract you would like to complete",
            position = 2
    )
    default ContractTeirEnum currentTier()
    {
        return ContractTeirEnum.BEGINNER;
    }

    // Use NPC Contact
    @ConfigItem(
            keyName = "useNpcContact",
            name = "Use NPC Contact",
            description = "Configures whether or not to use the NPC Contact spell to start a new contract",
            position = 3
    )
    default boolean useNpcContact()
    {
        return false;
    }

    // Use plank sack
    @ConfigItem(
            keyName = "usePlankSack",
            name = "Use Plank Sack",
            description = "Configures whether or not to use the Plank Sack to store planks",
            position = 4
    )
    default boolean usePlankSack()
    {
        return false;
    }


    @ConfigItem(
            keyName = HINT_ARROW_KEY,
            name = "Display Hint Arrows",
            description = "Configures whether or not to display the hint arrows",
            position = 1,
            hidden = true
    )
    default boolean displayHintArrows()
    {
        return true;
    }

    @ConfigItem(
            keyName = "showRequiredMaterials",
            name = "Display Required Materials",
            description = "Configures whether or not to display the required materials for your current task",
            position = 2
    )
    default boolean showRequiredMaterials()
    {
        return true;
    }

    @ConfigSection(
            name = "Highlight Options",
            description = "Settings related to the highlighting of objects and items",
            position = 100,
            closedByDefault = true
    )
    String highlightSection = "highlightSection";

    @ConfigItem(
            keyName = "highlightHotspots",
            name = "Highlight Building Hotspots",
            description = "Configures whether or not the building hotspots will be highlighted",
            section = highlightSection,
            position = 0
    )
    default boolean highlightHotspots()
    {
        return true;
    }

    @ConfigItem(
            keyName = "highlightStairs",
            name = "Highlight Stairs",
            description = "Configures whether or not the stairs will be highlighted",
            section = highlightSection,
            position = 2
    )
    default boolean highlightStairs()
    {
        return true;
    }

    @Alpha
    @ConfigItem(
            keyName = "highlightStairsColor",
            name = "Stairs Highlight Color",
            description = "Configures the color the stairs will be highlighted",
            section = highlightSection,
            position = 3,
            hidden = true
    )
    default Color highlightStairsColor()
    {
        return new Color(0, 255, 0, 20);
    }


    @ConfigSection(
            name = "Overlay Options",
            description = "Settings related to the overlay boxes",
            position = 200,
            closedByDefault = true
    )
    String overlaySection = "overlaySection";

    @ConfigItem(
            keyName = "textOverlay",
            name = "Display Text Overlay",
            description = "Configures whether or not the text overlay will be displayed for your current contract",
            section = overlaySection,
            position = 0,
            hidden = true
    )
    default boolean textOverlay()
    {
        return true;
    }

    @ConfigItem(
            keyName = "showSessionStats",
            name = "Display Session Stats",
            description = "Configures whether or not the amount of contracts and the points received from those contracts is displayed inside the overlay<br/>" +
                    "'Display Text Overlay' must be enabled for this to work",
            section = overlaySection,
            position = 1
    )
    default boolean showSessionStats()
    {
        return true;
    }

    // debug section and options
    @ConfigSection(
            name = "Debug Options",
            description = "Settings related to debugging",
            position = 300
    )
    String debugSection = "debugSection";

    // log messages in chat
    @ConfigItem(
            keyName = "logMessages",
            name = "Log Messages",
            description = "Configures whether or not to log messages in the chat",
            section = debugSection,
            position = 0
    )
    default boolean logMessages()
    {
        return true;
    }

}
