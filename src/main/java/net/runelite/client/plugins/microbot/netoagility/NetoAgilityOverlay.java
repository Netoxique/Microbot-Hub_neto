package net.runelite.client.plugins.microbot.netoagility;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class NetoAgilityOverlay extends OverlayPanel
{
	final NetoAgilityPlugin plugin;
	final NetoAgilityConfig config;

	@Inject
	NetoAgilityOverlay(NetoAgilityPlugin plugin, NetoAgilityConfig config)
	{
		super(plugin);
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
		setNaughty();
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (plugin.getAgilityScript().isShuttingDown() || !plugin.getAgilityScript().isRunning())
		{
			return null;
		}

		try
		{
			panelComponent.setPreferredSize(new Dimension(200, 300));
			panelComponent.getChildren().add(TitleComponent.builder()
				.text("Neto Agility V" + NetoAgilityPlugin.version)
				.color(Color.GREEN)
				.build());

			panelComponent.getChildren().add(LineComponent.builder().build());

			panelComponent.getChildren().add(LineComponent.builder()
				.left("Agility Exp")
				.right(Integer.toString(Microbot.getClient().getSkillExperience(Skill.AGILITY)))
				.build());

			panelComponent.getChildren().add(LineComponent.builder()
				.left("Current Obstacle")
				.right(Integer.toString(plugin.getAgilityScript().getCurrentObstacleIndex()))
				.build());

		}
		catch (Exception ex)
		{
			Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
		}
		return super.render(graphics);
	}
}
