package net.runelite.client.plugins.microbot.netomlm;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.plugins.microbot.netomlm.enums.MLMMiningSpotList;

@ConfigGroup(NetoMLMConfig.configGroup)
@ConfigInformation(
	"• This plugin will automate mining in motherload mine <br />" +
	"• The plugin prepares at the nearest bank and walks to Motherlode Mine <br />"
)
public interface NetoMLMConfig extends Config
{
	String configGroup = "neto-mlm";

	String antiCrash = "antiCrash";
	String dropGems = "dropGems";
	String useUpstairsMine = "useUpstairsMine";
	String useUpstairsHopper = "useUpstairsHopper";
	String miningArea = "miningArea";

	@ConfigSection(
		name = "General",
		description = "General Plugin Settings",
		position = 0
	)
	String generalSection = "general";

	@ConfigSection(
		name = "Features",
		description = "Feature Settings",
		position = 1
	)
	String featureSection = "features";

	@ConfigItem(
		keyName = antiCrash,
		name = "Anti Crash",
		description = "Avoids other players when mining in the lower level",
		position = 0,
		section = generalSection
	)
	default boolean useAntiCrash()
	{
		return false;
	}

	@ConfigItem(
		keyName = dropGems,
		name = "Drop Gems",
		description = "Automatically drop gems while mining",
		position = 1,
		section = generalSection
	)
	default boolean dropGems()
	{
		return true;
	}

	// Mine upstairs
	@ConfigItem(
		keyName = useUpstairsMine,
		name = "Mine Upstairs",
		description = "Should the plugin use the upstairs mining area",
		position = 0,
		section = featureSection
	)
	default boolean mineUpstairs()
	{
		return true;
	}

	// Upstairs hopper unlocked
	@ConfigItem(
		keyName = useUpstairsHopper,
		name = "Use Upstairs Hopper",
		description = "Should the plugin use the upstairs hopper",
		position = 1,
		section = featureSection
	)
	default boolean upstairsHopperUnlocked()
	{
		return true;
	}

	// Mining Area Selection
	@ConfigItem(
		keyName = miningArea,
		name = "Mining Area",
		description = "Choose the specific area to mine in Motherload Mine",
		position = 2,
		section = featureSection
	)
	default MLMMiningSpotList miningArea()
	{
		return MLMMiningSpotList.ANY;
	}
}
