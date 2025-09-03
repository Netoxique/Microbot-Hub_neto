package net.runelite.client.plugins.microbot.lunarbuckets;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class LunarBucketsOverlay extends OverlayPanel {
    @Inject
    LunarBucketsOverlay(LunarBucketsPlugin plugin) {
        super(plugin);
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        panelComponent.getChildren().add(TitleComponent.builder()
                .text("Lunar Buckets v" + LunarBucketsPlugin.version)
                .color(Color.CYAN)
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left(Microbot.status)
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Total profit:")
                .right(formatNumber(LunarBucketsScript.casts * LunarBucketsScript.profitPerCast))
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Profit/h:")
                .right(formatNumber(getProfitPerHour()))
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("XP/h:")
                .right(formatNumber(getXpPerHour()))
                .build());

        return super.render(graphics);
    }

    private int getProfitPerHour() {
        double hours = getElapsedHours();
        if (hours <= 0) return 0;
        return (int) (LunarBucketsScript.casts / hours * LunarBucketsScript.profitPerCast);
    }

    private int getXpPerHour() {
        double hours = getElapsedHours();
        if (hours <= 0) return 0;
        int currentXp = Microbot.getClient().getSkillExperience(Skill.MAGIC);
        int gained = currentXp - LunarBucketsScript.startMagicXp;
        return (int) (gained / hours);
    }

    private double getElapsedHours() {
        return (System.currentTimeMillis() - LunarBucketsScript.startTime) / 3600000d;
    }

    private String formatNumber(int value) {
        return String.format("%,d", value);
    }
}
