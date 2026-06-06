package net.runelite.client.plugins.microbot.netoarceuusrc;


import net.runelite.client.plugins.microbot.Microbot;
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

public class NetoArceuusRcOverlay extends OverlayPanel {

    private final NetoArceuusRcPlugin plugin;
    @Inject
    private NetoBreakManager breakManager;
    @Inject
    private NetoWorldHopManager worldHopManager;
    @Inject
    private NetoRuntimeDisable runtimeDisable;

    @Inject
    NetoArceuusRcOverlay(NetoArceuusRcPlugin plugin) {
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
                    .text("Neto Arceuus RC")
                    .color(Color.ORANGE)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder().build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Status: " + Microbot.status).right("Version: " + NetoArceuusRcPlugin.version)
                    .build());

            if (plugin.getNetoArceuusRcScript() != null) {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("State: " + plugin.getNetoArceuusRcScript().getState()).build()
                );
            }

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Trips:").right(worldHopManager.getTripsDisplay()).build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Break In:").right(breakManager.getBreakInDisplay()).build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Shutdown in:").right(runtimeDisable.getShutdownInDisplay()).build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Time Running:").right(TimeUtils.getFormattedDurationBetween(plugin.getStartTime(), Instant.now())).build());

        } catch (Exception ex) {
            Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
        }
        return super.render(graphics);
    }
}
