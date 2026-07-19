package net.runelite.client.plugins.microbot.xpautocanvas;

import net.runelite.api.Skill;
import net.runelite.client.plugins.xptracker.XpTrackerService;
import net.runelite.client.ui.overlay.components.ComponentConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class XpAutoCanvasOverlayTest
{
	private final XpTrackerService service = new StubXpTrackerService();

	@Test
	void formatsEveryMetric()
	{
		assertEquals("01:23", format(XpAutoCanvasMetric.TIME_TO_LEVEL));
		assertEquals("123", format(XpAutoCanvasMetric.XP_GAINED));
		assertEquals("456", format(XpAutoCanvasMetric.XP_HOUR));
		assertEquals("500", format(XpAutoCanvasMetric.XP_LEFT));
		assertEquals("7", format(XpAutoCanvasMetric.ACTIONS_LEFT));
		assertEquals("8", format(XpAutoCanvasMetric.ACTIONS_HOUR));
		assertEquals("9", format(XpAutoCanvasMetric.ACTIONS_DONE));
	}

	@Test
	void onlyLocalXpGainedWorksWithoutXpTracker()
	{
		for (XpAutoCanvasMetric metric : XpAutoCanvasMetric.values())
		{
			String expected = metric == XpAutoCanvasMetric.XP_GAINED ? "123" : "N/A";
			assertEquals(expected, XpAutoCanvasOverlay.formatMetric(metric, Skill.MINING, 1_000, 123, null, false));
		}
	}

	@Test
	void selectsCompactLayoutsAndPreservesProgressWidth()
	{
		assertEquals(XpAutoCanvasOverlay.LayoutMode.TWO_LINES, XpAutoCanvasOverlay.getLayoutMode(true, true));
		assertEquals(XpAutoCanvasOverlay.LayoutMode.ONE_LINE, XpAutoCanvasOverlay.getLayoutMode(true, false));
		assertEquals(XpAutoCanvasOverlay.LayoutMode.ONE_LINE, XpAutoCanvasOverlay.getLayoutMode(false, true));
		assertEquals(XpAutoCanvasOverlay.LayoutMode.PROGRESS_ONLY, XpAutoCanvasOverlay.getLayoutMode(false, false));
		assertEquals(ComponentConstants.STANDARD_WIDTH, XpAutoCanvasOverlay.getPanelWidth(XpAutoCanvasOverlay.LayoutMode.ONE_LINE, 16));
		assertEquals(ComponentConstants.STANDARD_WIDTH + 20, XpAutoCanvasOverlay.getPanelWidth(XpAutoCanvasOverlay.LayoutMode.PROGRESS_ONLY, 16));
	}

	private String format(XpAutoCanvasMetric metric)
	{
		return XpAutoCanvasOverlay.formatMetric(metric, Skill.MINING, 1_000, 123, service, true);
	}

	private static final class StubXpTrackerService implements XpTrackerService
	{
		@Override
		public int getActions(Skill skill)
		{
			return 9;
		}

		@Override
		public int getActionsHr(Skill skill)
		{
			return 8;
		}

		@Override
		public int getActionsLeft(Skill skill)
		{
			return 7;
		}

		@Override
		public int getXpHr(Skill skill)
		{
			return 456;
		}

		@Override
		public int getStartGoalXp(Skill skill)
		{
			return 0;
		}

		@Override
		public int getEndGoalXp(Skill skill)
		{
			return 1_500;
		}

		@Override
		public String getTimeTilGoal(Skill skill)
		{
			return "01:23";
		}
	}
}
