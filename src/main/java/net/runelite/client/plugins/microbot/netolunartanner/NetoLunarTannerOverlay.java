package net.runelite.client.plugins.microbot.netolunartanner;

import net.runelite.client.plugins.microbot.shared.session.NetoBreakManager;
import net.runelite.client.plugins.microbot.shared.session.NetoRuntimeDisable;
import net.runelite.client.plugins.microbot.shared.session.NetoWorldHopManager;
import net.runelite.client.plugins.microbot.util.misc.TimeUtils;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;
import java.time.Instant;

public class NetoLunarTannerOverlay extends OverlayPanel {
    private final NetoLunarTannerPlugin plugin;

    @Inject
    private NetoBreakManager breakManager;
    @Inject
    private NetoWorldHopManager worldHopManager;
    @Inject
    private NetoRuntimeDisable runtimeDisable;

    @Inject
    NetoLunarTannerOverlay(NetoLunarTannerPlugin plugin) {
        super(plugin);
        this.plugin = plugin;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.getChildren().clear();
            panelComponent.setPreferredSize(new Dimension(200, 300));

            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("Neto Tan Leather V" + NetoLunarTannerPlugin.version)
                    .color(Color.GREEN)
                    .build());

            // Display the combined hides and profit message
            panelComponent.getChildren().add(LineComponent.builder()
                    .left(NetoLunarTannerScript.combinedMessage)
                    .build());

            // Display World Hop Timer
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("World Hop In:")
                    .right(worldHopManager.getWorldHopDisplay())
                    .build());

            // Display Break Timer
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Break In:")
                    .right(breakManager.getBreakInDisplay())
                    .build());

            // Display Shutdown Timer
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Shutdown in:")
                    .right(runtimeDisable.getShutdownInDisplay())
                    .build());

            // Display Time Running duration
            if (plugin.getStartTime() != null) {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Time Running:")
                        .right(TimeUtils.getFormattedDurationBetween(plugin.getStartTime(), Instant.now()))
                        .build());
            }

        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }

        return super.render(graphics);
    }
}
