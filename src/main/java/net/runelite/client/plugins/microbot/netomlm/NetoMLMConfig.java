package net.runelite.client.plugins.microbot.netomlm;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.plugins.microbot.netomlm.enums.MLMMiningSpotList;
import net.runelite.client.plugins.microbot.shared.session.BreakSettings;
import net.runelite.client.plugins.microbot.shared.session.NetoWorldHopRegion;
import net.runelite.client.plugins.microbot.shared.session.RuntimeSettings;
import net.runelite.client.plugins.microbot.shared.session.WorldHopSettings;
import net.runelite.client.config.Range;

@ConfigGroup(NetoMLMConfig.configGroup)
@ConfigInformation(
	"• This plugin will automate mining in motherload mine <br />" +
	"• The plugin prepares at the nearest bank and walks to Motherlode Mine <br />"
)
public interface NetoMLMConfig extends Config, BreakSettings, WorldHopSettings, RuntimeSettings
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

	@ConfigSection(name = "World Jumping", description = "World jumping settings", position = 2)
	String worldJumpingSection = "World Jumping";

	@ConfigSection(name = "Breaks", description = "Break settings", position = 3)
	String breaksSection = "Breaks";

	@ConfigSection(name = "Runtime", description = "Runtime shutdown settings", position = 4)
	String runtimeSection = "Runtime";

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

	@ConfigItem(keyName = "enableWorldJumping", name = "Enable World Jumping", description = "Hop to another members world after a random number of minutes.", position = 1, section = worldJumpingSection)
	default boolean enableWorldJumping() { return true; }

	@Range(min = 1)
	@ConfigItem(keyName = "minMinutes", name = "Min. Minutes", description = "Minimum minutes before world jumping.", position = 2, section = worldJumpingSection)
	default int minMinutes() { return 20; }

	@Range(min = 1)
	@ConfigItem(keyName = "maxMinutes", name = "Max. Minutes", description = "Maximum minutes before world jumping.", position = 3, section = worldJumpingSection)
	default int maxMinutes() { return 30; }

	@ConfigItem(keyName = "worldJumpRegion", name = "Region", description = "World region to jump to.", position = 4, section = worldJumpingSection)
	default NetoWorldHopRegion worldJumpRegion() { return NetoWorldHopRegion.UNITED_STATES_OF_AMERICA; }

	@ConfigItem(keyName = "enableBreaks", name = "Enable", description = "Enable logout breaks.", position = 1, section = breaksSection)
	default boolean enableBreaks() { return true; }

	@Range(min = 1)
	@ConfigItem(keyName = "minPlaytime", name = "Min. Playtime", description = "Minimum playtime before a break, in minutes.", position = 2, section = breaksSection)
	default int minPlaytime() { return 60; }

	@Range(min = 1)
	@ConfigItem(keyName = "maxPlaytime", name = "Max. Playtime", description = "Maximum playtime before a break, in minutes.", position = 3, section = breaksSection)
	default int maxPlaytime() { return 90; }

	@Range(min = 1)
	@ConfigItem(keyName = "minBreak", name = "Min. Break", description = "Minimum break duration, in minutes.", position = 4, section = breaksSection)
	default int minBreak() { return 15; }

	@Range(min = 1)
	@ConfigItem(keyName = "maxBreak", name = "Max. Break", description = "Maximum break duration, in minutes.", position = 5, section = breaksSection)
	default int maxBreak() { return 20; }

	@ConfigItem(keyName = "enableRuntime", name = "Enable", description = "Enable runtime limit.", position = 0, section = runtimeSection)
	default boolean enableRuntime() { return true; }

	@Range(min = 1)
	@ConfigItem(keyName = "minRuntime", name = "Min. Runtime", description = "Minimum runtime before stopping the plugin, in minutes.", position = 1, section = runtimeSection)
	default int minRuntime() { return 420; }

	@Range(min = 1)
	@ConfigItem(keyName = "maxRuntime", name = "Max. Runtime", description = "Maximum runtime before stopping the plugin, in minutes.", position = 2, section = runtimeSection)
	default int maxRuntime() { return 480; }
}
