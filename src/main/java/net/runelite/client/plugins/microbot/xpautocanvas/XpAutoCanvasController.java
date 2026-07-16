package net.runelite.client.plugins.microbot.xpautocanvas;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import net.runelite.api.Skill;

class XpAutoCanvasController
{
	private final Map<Skill, Queue<Integer>> recentDrops = new EnumMap<>(Skill.class);
	private final Map<Skill, Integer> lastDropTicks = new EnumMap<>(Skill.class);
	private final Map<Skill, Integer> lastExperience = new EnumMap<>(Skill.class);
	private final Map<Skill, Integer> sessionXpGained = new EnumMap<>(Skill.class);
	private final Set<Skill> activeOverlays = EnumSet.noneOf(Skill.class);

	void initializeExperience(Skill skill, int experience)
	{
		lastExperience.put(skill, experience);
		sessionXpGained.put(skill, 0);
	}

	boolean onExperienceChanged(Skill skill, int experience)
	{
		Integer previousExperience = lastExperience.put(skill, experience);
		if (previousExperience == null || experience <= previousExperience)
		{
			return false;
		}

		sessionXpGained.merge(skill, experience - previousExperience, Integer::sum);
		return true;
	}

	int getSessionXpGained(Skill skill)
	{
		return sessionXpGained.getOrDefault(skill, 0);
	}

	void resetExperience()
	{
		lastExperience.clear();
		sessionXpGained.clear();
	}

	void onXpDrop(Skill skill, int currentTick, int requiredDrops, int activationWindowTicks)
	{
		Queue<Integer> drops = recentDrops.computeIfAbsent(skill, ignored -> new ArrayDeque<>());
		drops.add(currentTick);
		prune(drops, currentTick, activationWindowTicks);
		lastDropTicks.put(skill, currentTick);
		if (drops.size() >= Math.max(1, requiredDrops))
		{
			activeOverlays.add(skill);
		}
	}

	void expireInactive(int currentTick, int hideAfterTicks)
	{
		if (hideAfterTicks <= 0)
		{
			return;
		}
		activeOverlays.removeIf(skill -> currentTick - lastDropTicks.getOrDefault(skill, currentTick) >= hideAfterTicks);
	}

	boolean isActive(Skill skill)
	{
		return activeOverlays.contains(skill);
	}

	Set<Skill> getActiveSkills()
	{
		return Collections.unmodifiableSet(activeOverlays);
	}

	void reset()
	{
		recentDrops.clear();
		lastDropTicks.clear();
		activeOverlays.clear();
	}

	private static void prune(Queue<Integer> drops, int currentTick, int activationWindowTicks)
	{
		int oldestAllowedTick = currentTick - Math.max(0, activationWindowTicks);
		while (!drops.isEmpty() && drops.peek() < oldestAllowedTick)
		{
			drops.remove();
		}
	}
}
