package net.runelite.client.plugins.microbot.xpautocanvas;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import net.runelite.api.Client;
import net.runelite.api.Experience;
import net.runelite.api.Skill;
import net.runelite.client.plugins.xptracker.XpTrackerService;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.SkillColor;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.components.ComponentOrientation;
import net.runelite.client.ui.overlay.components.ImageComponent;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.ProgressBarComponent;
import net.runelite.client.ui.overlay.components.SplitComponent;
import net.runelite.client.util.QuantityFormatter;

final class XpAutoCanvasOverlay extends OverlayPanel
{
	private static final Rectangle PANEL_BORDER = new Rectangle(2, 2, 2, 2);
	private final PanelComponent detailsPanel = new PanelComponent();
	private final Client client;
	private final XpTrackerService xpTrackerService;
	private final Skill skill;
	private final BufferedImage icon;

	XpAutoCanvasOverlay(XpAutoCanvasPlugin plugin, Client client, XpTrackerService xpTrackerService, Skill skill, BufferedImage icon)
	{
		super(plugin);
		this.client = client;
		this.xpTrackerService = xpTrackerService;
		this.skill = skill;
		this.icon = icon;
		panelComponent.setBorder(PANEL_BORDER);
		panelComponent.setGap(new Point(0, 2));
		detailsPanel.setBorder(new Rectangle(2, 1, 4, 0));
		detailsPanel.setBackgroundColor(null);
	}

	Skill getSkill()
	{
		return skill;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		panelComponent.getChildren().clear();
		detailsPanel.getChildren().clear();
		graphics.setFont(FontManager.getRunescapeSmallFont());

		int currentXp = client.getSkillExperience(skill);
		int currentLevel = Experience.getLevelForXp(currentXp);
		int goalXp = xpTrackerService == null ? 0 : xpTrackerService.getEndGoalXp(skill);
		if (goalXp <= currentXp || goalXp > Experience.MAX_SKILL_XP)
		{
			goalXp = currentLevel >= Experience.MAX_REAL_LEVEL
				? Experience.MAX_SKILL_XP
				: Experience.getXpForLevel(currentLevel + 1);
		}
		int startXp = currentLevel >= Experience.MAX_REAL_LEVEL
			? Experience.getXpForLevel(Experience.MAX_REAL_LEVEL)
			: Experience.getXpForLevel(currentLevel);
		int goalLevel = goalXp == Experience.MAX_SKILL_XP ? Experience.MAX_REAL_LEVEL : Experience.getLevelForXp(goalXp);

		LineComponent xpRate = LineComponent.builder()
			.left("XP/hr:")
			.right(QuantityFormatter.quantityToStackSize(xpTrackerService == null ? 0 : xpTrackerService.getXpHr(skill)))
			.build();
		LineComponent xpLeft = LineComponent.builder()
			.left("XP left:")
			.right(QuantityFormatter.quantityToStackSize(Math.max(0, goalXp - currentXp)))
			.build();
		SplitComponent lines = SplitComponent.builder()
			.first(xpRate)
			.second(xpLeft)
			.orientation(ComponentOrientation.VERTICAL)
			.build();
		SplitComponent iconAndLines = SplitComponent.builder()
			.first(new ImageComponent(icon))
			.second(lines)
			.orientation(ComponentOrientation.HORIZONTAL)
			.gap(new Point(4, 0))
			.build();
		detailsPanel.getChildren().add(iconAndLines);

		ProgressBarComponent progress = new ProgressBarComponent();
		progress.setBackgroundColor(new Color(61, 56, 49));
		progress.setForegroundColor(SkillColor.find(skill).getColor());
		progress.setMinimum(startXp);
		progress.setMaximum(Math.max(startXp + 1, goalXp));
		progress.setValue(currentXp);
		progress.setLeftLabel(String.valueOf(currentLevel));
		progress.setRightLabel(goalXp == Experience.MAX_SKILL_XP ? "200M" : String.valueOf(goalLevel));

		panelComponent.getChildren().add(detailsPanel);
		panelComponent.getChildren().add(progress);
		return super.render(graphics);
	}

	@Override
	public String getName()
	{
		return super.getName() + " " + skill.getName();
	}
}
