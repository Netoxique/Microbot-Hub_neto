package net.runelite.client.plugins.microbot.xpautocanvas;

import com.google.inject.Provides;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.StatChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.xptracker.XpTrackerPlugin;
import net.runelite.client.plugins.xptracker.XpTrackerService;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
	name = PluginConstants.DEFAULT_PREFIX + "Automatic XP Canvas",
	description = "Automatically shows active skills on the XP tracker canvas",
	tags = {"xp", "experience", "tracker", "canvas"},
	authors = {"Netoxic"},
	version = XpAutoCanvasPlugin.version,
	minClientVersion = "2.0.61",
	enabledByDefault = PluginConstants.DEFAULT_ENABLED,
	isExternal = PluginConstants.IS_EXTERNAL
)
public class XpAutoCanvasPlugin extends Plugin
{
	static final String version = "1.0.1";
	private static final double GAME_TICK_SECONDS = 0.6;
	private static final Set<Skill> COMBAT_SKILLS = EnumSet.of(
		Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.RANGED, Skill.MAGIC);
	private static final Set<Skill> COMBAT_AND_HP_SKILLS = EnumSet.of(
		Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.RANGED, Skill.MAGIC, Skill.HITPOINTS);

	@Inject private Client client;
	@Inject private XpAutoCanvasConfig config;
	@Inject private OverlayManager overlayManager;
	@Inject private PluginManager pluginManager;
	@Inject private SkillIconManager skillIconManager;
	private XpTrackerService xpTrackerService;

	private final XpAutoCanvasController controller = new XpAutoCanvasController();
	private final Set<Skill> tickGains = EnumSet.noneOf(Skill.class);
	private final Map<Skill, Integer> lastExperience = new EnumMap<>(Skill.class);

	@Provides
	XpAutoCanvasConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(XpAutoCanvasConfig.class);
	}

	@Override
	protected void startUp()
	{
		xpTrackerService = pluginManager.getPlugins().stream()
			.filter(XpTrackerPlugin.class::isInstance)
			.map(XpTrackerPlugin.class::cast)
			.map(plugin -> plugin.getInjector().getInstance(XpTrackerService.class))
			.findFirst()
			.orElse(null);
		for (Skill skill : Skill.values())
		{
			lastExperience.put(skill, client.getSkillExperience(skill));
		}
	}

	@Override
	protected void shutDown()
	{
		removeAllAutoOverlays();
		controller.reset();
		tickGains.clear();
		lastExperience.clear();
		xpTrackerService = null;
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		Skill skill = event.getSkill();
		Integer previousXp = lastExperience.put(skill, event.getXp());
		if (config.enabled() && previousXp != null && event.getXp() > previousXp)
		{
			tickGains.add(skill);
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (!config.enabled())
		{
			removeAllAutoOverlays();
			controller.reset();
			tickGains.clear();
			return;
		}

		Set<Skill> filteredGains = filterTickGains(
			tickGains, config.disableHpOnCombat(), config.disableCombatOnSlayer());
		tickGains.clear();

		int currentTick = client.getTickCount();
		int activationWindowTicks = secondsToTicks(config.activationWindow());
		for (Skill skill : filteredGains)
		{
			controller.onXpDrop(skill, currentTick, config.requiredDrops(), activationWindowTicks);
		}
		controller.expireInactive(currentTick, config.hideAfter() == 0 ? 0 : secondsToTicks(config.hideAfter()));
		synchronizeOverlays();
	}

	private void synchronizeOverlays()
	{
		for (Skill skill : Skill.values())
		{
			boolean shouldShow = controller.isActive(skill) && !hasCoreOverlay(skill);
			boolean isShown = hasAutoOverlay(skill);
			if (shouldShow && !isShown)
			{
				overlayManager.add(new XpAutoCanvasOverlay(this, client, xpTrackerService, skill, skillIconManager.getSkillImage(skill)));
			}
			else if (!shouldShow && isShown)
			{
				removeAutoOverlay(skill);
			}
		}
	}

	private boolean hasCoreOverlay(Skill skill)
	{
		boolean trackerActive = pluginManager.getPlugins().stream()
			.filter(XpTrackerPlugin.class::isInstance)
			.anyMatch(pluginManager::isPluginActive);
		return trackerActive && overlayManager.anyMatch(overlay ->
			overlay.getClass().getName().equals("net.runelite.client.plugins.xptracker.XpInfoBoxOverlay")
				&& overlay.getName().endsWith(skill.getName()));
	}

	private boolean hasAutoOverlay(Skill skill)
	{
		return overlayManager.anyMatch(overlay -> overlay instanceof XpAutoCanvasOverlay
			&& ((XpAutoCanvasOverlay) overlay).getSkill() == skill);
	}

	private void removeAutoOverlay(Skill skill)
	{
		overlayManager.removeIf(overlay -> overlay instanceof XpAutoCanvasOverlay
			&& ((XpAutoCanvasOverlay) overlay).getSkill() == skill);
	}

	private void removeAllAutoOverlays()
	{
		overlayManager.removeIf(overlay -> overlay instanceof XpAutoCanvasOverlay);
	}

	static int secondsToTicks(int seconds)
	{
		return Math.max(1, (int) Math.round(seconds / GAME_TICK_SECONDS));
	}

	static Set<Skill> filterTickGains(Set<Skill> gains, boolean suppressHitpoints, boolean suppressCombatOnSlayer)
	{
		Set<Skill> filtered = gains.isEmpty() ? EnumSet.noneOf(Skill.class) : EnumSet.copyOf(gains);
		if (suppressHitpoints && filtered.contains(Skill.HITPOINTS)
			&& filtered.stream().anyMatch(COMBAT_SKILLS::contains))
		{
			filtered.remove(Skill.HITPOINTS);
		}
		if (suppressCombatOnSlayer && filtered.contains(Skill.SLAYER))
		{
			filtered.removeAll(COMBAT_AND_HP_SKILLS);
		}
		return filtered;
	}
}
