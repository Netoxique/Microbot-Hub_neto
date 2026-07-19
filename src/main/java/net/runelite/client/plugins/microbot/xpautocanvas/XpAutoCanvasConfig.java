package net.runelite.client.plugins.microbot.xpautocanvas;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup(XpAutoCanvasConfig.GROUP)
public interface XpAutoCanvasConfig extends Config
{
	String GROUP = "xp-auto-canvas";

	@ConfigItem(keyName = "enabled", name = "Enable automation", description = "Automatically show and hide XP canvas overlays", position = 0)
	default boolean enabled()
	{
		return false;
	}

	@ConfigItem(keyName = "showTopTracker", name = "Show top tracker", description = "Show the first statistic row", position = 1)
	default boolean showTopTracker()
	{
		return true;
	}

	@ConfigItem(keyName = "topTracker", name = "Top tracker", description = "Information displayed in the first statistic row", position = 2)
	default XpAutoCanvasMetric topTracker()
	{
		return XpAutoCanvasMetric.XP_HOUR;
	}

	@ConfigItem(keyName = "showBottomTracker", name = "Show bottom tracker", description = "Show the second statistic row", position = 3)
	default boolean showBottomTracker()
	{
		return true;
	}

	@ConfigItem(keyName = "bottomTracker", name = "Bottom tracker", description = "Information displayed in the second statistic row", position = 4)
	default XpAutoCanvasMetric bottomTracker()
	{
		return XpAutoCanvasMetric.XP_LEFT;
	}

	@Range(min = 1, max = 20)
	@ConfigItem(keyName = "requiredDrops", name = "Required XP drops", description = "XP drops required inside the activation window", position = 5)
	default int requiredDrops()
	{
		return 2;
	}

	@Range(min = 1, max = 300)
	@ConfigItem(keyName = "activationWindow", name = "Activation window", description = "Seconds in which the required XP drops must occur", position = 6)
	default int activationWindow()
	{
		return 10;
	}

	@Range(min = 0, max = 3600)
	@ConfigItem(keyName = "hideAfter", name = "Hide after", description = "Seconds without XP before hiding an overlay (0 disables hiding)", position = 7)
	default int hideAfter()
	{
		return 30;
	}

	@ConfigItem(keyName = "disableHpOnCombat", name = "Suppress Hitpoints", description = "Ignore Hitpoints XP when another combat skill gains XP on the same tick", position = 8)
	default boolean disableHpOnCombat()
	{
		return true;
	}

	@ConfigItem(keyName = "disableCombatOnSlayer", name = "Suppress combat on Slayer", description = "Ignore combat XP when Slayer gains XP on the same tick", position = 9)
	default boolean disableCombatOnSlayer()
	{
		return true;
	}
}
