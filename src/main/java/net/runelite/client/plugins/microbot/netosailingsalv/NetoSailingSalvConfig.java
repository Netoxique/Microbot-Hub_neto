package net.runelite.client.plugins.microbot.netosailingsalv;

import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.plugins.microbot.netosailingsalv.features.trials.data.TrialRanks;

import java.awt.*;

@ConfigGroup(NetoSailingSalvConfig.configGroup)
public interface NetoSailingSalvConfig extends Config {
	String configGroup = "neto-sailing-salv";

	@ConfigSection(
		name = "General",
		description = "General Plugin Settings",
		position = 0
	)
	String generalSection = "general";

	@ConfigSection(
		name = "Salvaging Highlight",
		description = "Shipwreck highlighting settings",
		position = 1
	)
	String highlightSection = "highlight";

	@ConfigSection(
		name = "Trials",
		description = "Barracuda Trials settings",
		position = 2
	)
	String trialsSection = "trials";

	@ConfigItem(
		keyName = "Salvgaging",
		name = "Salvgaging",
		description = "Enable this option to use salvaging.",
		position = 0,
		section = generalSection
	)
	default boolean salvaging()
	{
		return false;
	}

	@ConfigItem(
		keyName = "minimumAlchValue",
		name = "Min. Alch value",
		description = "Minimum High Level Alchemy value for automatic alching.",
		position = 1,
		section = generalSection
	)
	default String minimumAlchValue()
	{
		return "2000";
	}

	@ConfigItem(
		keyName = "netoSalvagingHighlight",
		name = "Enable Highlighting",
		description = "Enable shipwreck highlighting overlay.",
		position = 0,
		section = highlightSection
	)
	default boolean netoSalvagingHighlight()
	{
		return true;
	}

	@ConfigItem(
		keyName = "netoSalvagingHighlightActiveWrecks",
		name = "Highlight Active Wrecks",
		description = "Highlight shipwrecks you can salvage.",
		position = 1,
		section = highlightSection
	)
	default boolean netoSalvagingHighlightActiveWrecks()
	{
		return true;
	}

	@Alpha
	@ConfigItem(
		keyName = "netoSalvagingHighlightActiveWrecksColour",
		name = "Active Wrecks Colour",
		description = "Colour for active shipwrecks.",
		position = 2,
		section = highlightSection
	)
	default Color netoSalvagingHighlightActiveWrecksColour()
	{
		return Color.GREEN;
	}

	@ConfigItem(
		keyName = "netoSalvagingHighlightInactiveWrecks",
		name = "Highlight Inactive Wrecks",
		description = "Highlight depleted shipwrecks (stumps).",
		position = 3,
		section = highlightSection
	)
	default boolean netoSalvagingHighlightInactiveWrecks()
	{
		return false;
	}

	@Alpha
	@ConfigItem(
		keyName = "netoSalvagingHighlightInactiveWrecksColour",
		name = "Inactive Wrecks Colour",
		description = "Colour for inactive shipwrecks (stumps).",
		position = 4,
		section = highlightSection
	)
	default Color netoSalvagingHighlightInactiveWrecksColour()
	{
		return Color.GRAY;
	}

	@ConfigItem(
		keyName = "netoSalvagingHighlightHighLevelWrecks",
		name = "Highlight High Level Wrecks",
		description = "Highlight shipwrecks above your sailing level.",
		position = 5,
		section = highlightSection
	)
	default boolean netoSalvagingHighlightHighLevelWrecks()
	{
		return false;
	}

	@Alpha
	@ConfigItem(
		keyName = "salvagingHighLevelWrecksColour",
		name = "High Level Wrecks Colour",
		description = "Colour for shipwrecks above your level.",
		position = 6,
		section = highlightSection
	)
	default Color salvagingHighLevelWrecksColour()
	{
		return Color.RED;
	}

	@ConfigItem(
		keyName = "trials",
		name = "Enable Trials",
		description = "Enable Barracuda Trials automation.",
		position = 0,
		section = trialsSection
	)
	default boolean trials()
	{
		return false;
	}

	@ConfigItem(
		keyName = "trialsRank",
		name = "Target Rank",
		description = "The rank route to follow during trials.",
		position = 1,
		section = trialsSection
	)
	default TrialRanks trialsRank()
	{
		return TrialRanks.Swordfish;
	}

	@ConfigItem(
		keyName = "showTrialRoute",
		name = "Show Route Overlay",
		description = "Show the trial route path on screen.",
		position = 2,
		section = trialsSection
	)
	default boolean showTrialRoute()
	{
		return true;
	}

	@ConfigItem(
		keyName = "autoNavigate",
		name = "Auto Navigate",
		description = "Automatically navigate the boat along the route.",
		position = 3,
		section = trialsSection
	)
	default boolean autoNavigate()
	{
		return false;
	}
}
