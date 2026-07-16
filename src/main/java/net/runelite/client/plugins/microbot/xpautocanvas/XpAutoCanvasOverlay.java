package net.runelite.client.plugins.microbot.xpautocanvas;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.function.BooleanSupplier;
import net.runelite.api.Client;
import net.runelite.api.Experience;
import net.runelite.api.Skill;
import net.runelite.client.plugins.xptracker.XpTrackerService;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.SkillColor;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.components.ComponentConstants;
import net.runelite.client.ui.overlay.components.ComponentOrientation;
import net.runelite.client.ui.overlay.components.ImageComponent;
import net.runelite.client.ui.overlay.components.LayoutableRenderableEntity;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.ProgressBarComponent;
import net.runelite.client.ui.overlay.components.SplitComponent;
import net.runelite.client.util.QuantityFormatter;

final class XpAutoCanvasOverlay extends OverlayPanel
{
	enum LayoutMode
	{
		TWO_LINES,
		ONE_LINE,
		PROGRESS_ONLY
	}

	private static final int ICON_GAP = 4;
	private static final Rectangle PANEL_BORDER = new Rectangle(2, 2, 2, 2);
	private final PanelComponent detailsPanel = new PanelComponent();
	private final Client client;
	private final XpAutoCanvasConfig config;
	private final XpAutoCanvasController controller;
	private final XpTrackerService xpTrackerService;
	private final BooleanSupplier xpTrackerActive;
	private final Skill skill;
	private final BufferedImage normalIcon;
	private final BufferedImage smallIcon;

	XpAutoCanvasOverlay(
		XpAutoCanvasPlugin plugin,
		Client client,
		XpAutoCanvasConfig config,
		XpAutoCanvasController controller,
		XpTrackerService xpTrackerService,
		BooleanSupplier xpTrackerActive,
		Skill skill,
		BufferedImage normalIcon,
		BufferedImage smallIcon)
	{
		super(plugin);
		this.client = client;
		this.config = config;
		this.controller = controller;
		this.xpTrackerService = xpTrackerService;
		this.xpTrackerActive = xpTrackerActive;
		this.skill = skill;
		this.normalIcon = normalIcon;
		this.smallIcon = smallIcon;
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

		boolean showTop = config.showTopTracker();
		boolean showBottom = config.showBottomTracker();
		LayoutMode layoutMode = getLayoutMode(showTop, showBottom);
		ProgressBarComponent progress = buildProgressBar();
		panelComponent.setPreferredSize(new Dimension(getPanelWidth(layoutMode, smallIcon.getWidth()), 0));

		if (layoutMode == LayoutMode.PROGRESS_ONLY)
		{
			panelComponent.getChildren().add(SplitComponent.builder()
				.first(new ImageComponent(smallIcon))
				.second(progress)
				.orientation(ComponentOrientation.HORIZONTAL)
				.gap(new Point(ICON_GAP, 0))
				.build());
			return super.render(graphics);
		}

		LineComponent firstLine = showTop ? buildMetricLine(config.topTracker()) : buildMetricLine(config.bottomTracker());
		LayoutableRenderableEntity statisticLines = firstLine;
		if (layoutMode == LayoutMode.TWO_LINES)
		{
			statisticLines = SplitComponent.builder()
				.first(firstLine)
				.second(buildMetricLine(config.bottomTracker()))
				.orientation(ComponentOrientation.VERTICAL)
				.build();
		}

		SplitComponent iconAndLines = SplitComponent.builder()
			.first(new ImageComponent(layoutMode == LayoutMode.TWO_LINES ? normalIcon : smallIcon))
			.second(statisticLines)
			.orientation(ComponentOrientation.HORIZONTAL)
			.gap(new Point(ICON_GAP, 0))
			.build();
		detailsPanel.getChildren().add(iconAndLines);
		panelComponent.getChildren().add(detailsPanel);
		panelComponent.getChildren().add(progress);
		return super.render(graphics);
	}

	private LineComponent buildMetricLine(XpAutoCanvasMetric metric)
	{
		return LineComponent.builder()
			.left(metric.getOverlayLabel() + ":")
			.right(formatMetric(
				metric,
				skill,
				client.getSkillExperience(skill),
				controller.getSessionXpGained(skill),
				xpTrackerService,
				xpTrackerActive.getAsBoolean()))
			.build();
	}

	private ProgressBarComponent buildProgressBar()
	{
		int currentXp = client.getSkillExperience(skill);
		int currentLevel = Experience.getLevelForXp(currentXp);
		int goalXp = getGoalXp(currentXp, currentLevel);
		int startXp = currentLevel >= Experience.MAX_REAL_LEVEL
			? Experience.getXpForLevel(Experience.MAX_REAL_LEVEL)
			: Experience.getXpForLevel(currentLevel);
		int goalLevel = goalXp == Experience.MAX_SKILL_XP ? Experience.MAX_REAL_LEVEL : Experience.getLevelForXp(goalXp);

		ProgressBarComponent progress = new ProgressBarComponent();
		progress.setBackgroundColor(new Color(61, 56, 49));
		progress.setForegroundColor(SkillColor.find(skill).getColor());
		progress.setMinimum(startXp);
		progress.setMaximum(Math.max(startXp + 1, goalXp));
		progress.setValue(currentXp);
		progress.setLeftLabel(String.valueOf(currentLevel));
		progress.setRightLabel(goalXp == Experience.MAX_SKILL_XP ? "200M" : String.valueOf(goalLevel));
		return progress;
	}

	private int getGoalXp(int currentXp, int currentLevel)
	{
		int goalXp = xpTrackerService == null || !xpTrackerActive.getAsBoolean()
			? 0
			: xpTrackerService.getEndGoalXp(skill);
		if (goalXp <= currentXp || goalXp > Experience.MAX_SKILL_XP)
		{
			return currentLevel >= Experience.MAX_REAL_LEVEL
				? Experience.MAX_SKILL_XP
				: Experience.getXpForLevel(currentLevel + 1);
		}
		return goalXp;
	}

	static String formatMetric(
		XpAutoCanvasMetric metric,
		Skill skill,
		int currentXp,
		int sessionXpGained,
		XpTrackerService service,
		boolean serviceAvailable)
	{
		if (metric == XpAutoCanvasMetric.XP_GAINED)
		{
			return formatNumber(sessionXpGained);
		}
		if (!serviceAvailable || service == null)
		{
			return "N/A";
		}

		switch (metric)
		{
			case TIME_TO_LEVEL:
				String time = service.getTimeTilGoal(skill);
				return time == null || time.isEmpty() ? "N/A" : time;
			case XP_HOUR:
				return formatNumber(service.getXpHr(skill));
			case XP_LEFT:
				int goalXp = service.getEndGoalXp(skill);
				return goalXp <= currentXp ? "N/A" : formatNumber(goalXp - currentXp);
			case ACTIONS_LEFT:
				return formatNumber(service.getActionsLeft(skill));
			case ACTIONS_HOUR:
				return formatNumber(service.getActionsHr(skill));
			case ACTIONS_DONE:
				return formatNumber(service.getActions(skill));
			default:
				return "N/A";
		}
	}

	private static String formatNumber(int value)
	{
		return value == Integer.MAX_VALUE ? "N/A" : QuantityFormatter.quantityToRSDecimalStack(value, true);
	}

	static LayoutMode getLayoutMode(boolean showTop, boolean showBottom)
	{
		if (showTop && showBottom)
		{
			return LayoutMode.TWO_LINES;
		}
		return showTop || showBottom ? LayoutMode.ONE_LINE : LayoutMode.PROGRESS_ONLY;
	}

	static int getPanelWidth(LayoutMode layoutMode, int smallIconWidth)
	{
		return layoutMode == LayoutMode.PROGRESS_ONLY
			? ComponentConstants.STANDARD_WIDTH + smallIconWidth + ICON_GAP
			: ComponentConstants.STANDARD_WIDTH;
	}

	@Override
	public String getName()
	{
		return super.getName() + " " + skill.getName();
	}
}
