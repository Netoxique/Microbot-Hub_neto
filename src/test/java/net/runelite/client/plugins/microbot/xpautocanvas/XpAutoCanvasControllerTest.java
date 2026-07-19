package net.runelite.client.plugins.microbot.xpautocanvas;

import java.util.EnumSet;
import java.util.Set;
import net.runelite.api.Skill;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XpAutoCanvasControllerTest
{
	@Test
	void activatesAfterRequiredDropsInsideSlidingWindow()
	{
		XpAutoCanvasController controller = new XpAutoCanvasController();
		controller.onXpDrop(Skill.WOODCUTTING, 100, 2, 10);
		assertFalse(controller.isActive(Skill.WOODCUTTING));

		controller.onXpDrop(Skill.WOODCUTTING, 110, 2, 10);
		assertTrue(controller.isActive(Skill.WOODCUTTING));
	}

	@Test
	void prunesDropsOutsideSlidingWindow()
	{
		XpAutoCanvasController controller = new XpAutoCanvasController();
		controller.onXpDrop(Skill.MINING, 100, 2, 10);
		controller.onXpDrop(Skill.MINING, 111, 2, 10);
		assertFalse(controller.isActive(Skill.MINING));
	}

	@Test
	void expiresAtInactivityThreshold()
	{
		XpAutoCanvasController controller = new XpAutoCanvasController();
		controller.onXpDrop(Skill.FISHING, 50, 1, 10);
		controller.expireInactive(59, 10);
		assertTrue(controller.isActive(Skill.FISHING));

		controller.expireInactive(60, 10);
		assertFalse(controller.isActive(Skill.FISHING));
	}

	@Test
	void zeroHideThresholdKeepsOverlayActive()
	{
		XpAutoCanvasController controller = new XpAutoCanvasController();
		controller.onXpDrop(Skill.COOKING, 1, 1, 10);
		controller.expireInactive(10_000, 0);
		assertTrue(controller.isActive(Skill.COOKING));
	}

	@Test
	void convertsSecondsUsingRoundedGameTicks()
	{
		assertEquals(17, XpAutoCanvasPlugin.secondsToTicks(10));
		assertEquals(50, XpAutoCanvasPlugin.secondsToTicks(30));
	}

	@Test
	void suppressesHitpointsWhenCombatXpArrivesOnSameTick()
	{
		Set<Skill> filtered = XpAutoCanvasPlugin.filterTickGains(
			EnumSet.of(Skill.ATTACK, Skill.HITPOINTS), true, false);
		assertEquals(EnumSet.of(Skill.ATTACK), filtered);
	}

	@Test
	void slayerSuppressesCombatAndHitpointsButKeepsOtherSkills()
	{
		Set<Skill> filtered = XpAutoCanvasPlugin.filterTickGains(
			EnumSet.of(Skill.SLAYER, Skill.RANGED, Skill.HITPOINTS, Skill.PRAYER), true, true);
		assertEquals(EnumSet.of(Skill.SLAYER, Skill.PRAYER), filtered);
	}

	@Test
	void tracksPositiveSessionExperienceWithoutCountingDecreases()
	{
		XpAutoCanvasController controller = new XpAutoCanvasController();
		controller.initializeExperience(Skill.WOODCUTTING, 1_000);

		assertTrue(controller.onExperienceChanged(Skill.WOODCUTTING, 1_125));
		assertEquals(125, controller.getSessionXpGained(Skill.WOODCUTTING));
		assertFalse(controller.onExperienceChanged(Skill.WOODCUTTING, 1_100));
		assertEquals(125, controller.getSessionXpGained(Skill.WOODCUTTING));
		assertTrue(controller.onExperienceChanged(Skill.WOODCUTTING, 1_150));
		assertEquals(175, controller.getSessionXpGained(Skill.WOODCUTTING));
	}

	@Test
	void resetsSessionExperienceIndependentlyFromOverlayTracking()
	{
		XpAutoCanvasController controller = new XpAutoCanvasController();
		controller.initializeExperience(Skill.MINING, 500);
		controller.onExperienceChanged(Skill.MINING, 550);
		controller.resetExperience();

		assertEquals(0, controller.getSessionXpGained(Skill.MINING));
		assertFalse(controller.onExperienceChanged(Skill.MINING, 600));
	}
}
