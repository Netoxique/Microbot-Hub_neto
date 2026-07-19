package net.runelite.client.plugins.microbot.xpautocanvas;

import com.google.inject.Provides;
import java.util.EnumSet;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.StatChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.xptracker.XpTrackerPlugin;
import net.runelite.client.plugins.xptracker.XpTrackerService;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
	name = "Neto Auto XP Canvas",
	description = "Automatically shows active skills on the XP tracker canvas",
	tags = {"xp", "experience", "tracker", "canvas"},
	authors = {"Netoxic"},
	version = XpAutoCanvasPlugin.version,
	minClientVersion = "2.0.61",
	enabledByDefault = false,
	isExternal = true
)
public class XpAutoCanvasPlugin extends Plugin
{
	static final String version = "1.1.1";
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
			controller.initializeExperience(skill, client.getSkillExperience(skill));
		}
	}

	@Override
	protected void shutDown()
	{
		removeAllAutoOverlays();
		controller.reset();
		controller.resetExperience();
		tickGains.clear();
		xpTrackerService = null;
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		Skill skill = event.getSkill();
		boolean gainedExperience = controller.onExperienceChanged(skill, event.getXp());
		if (config.enabled() && gainedExperience)
		{
			tickGains.add(skill);
		}
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		removeAllAutoOverlays();
		controller.reset();
		controller.resetExperience();
		tickGains.clear();
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
				overlayManager.add(new XpAutoCanvasOverlay(
					this,
					client,
					config,
					controller,
					xpTrackerService,
					this::isXpTrackerActive,
					skill,
					skillIconManager.getSkillImage(skill),
					skillIconManager.getSkillImage(skill, true)));
			}
			else if (!shouldShow && isShown)
			{
				removeAutoOverlay(skill);
			}
		}
	}

	private boolean hasCoreOverlay(Skill skill)
	{
		return isXpTrackerActive() && overlayManager.anyMatch(overlay ->
			overlay.getClass().getName().equals("net.runelite.client.plugins.xptracker.XpInfoBoxOverlay")
				&& overlay.getName().endsWith(skill.getName()));
	}

	private boolean isXpTrackerActive()
	{
		return pluginManager.getPlugins().stream()
			.filter(XpTrackerPlugin.class::isInstance)
			.anyMatch(pluginManager::isPluginActive);
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
