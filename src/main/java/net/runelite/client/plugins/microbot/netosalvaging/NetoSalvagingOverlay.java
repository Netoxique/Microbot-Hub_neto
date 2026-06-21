package net.runelite.client.plugins.microbot.netosalvaging;

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

public class NetoSalvagingOverlay extends OverlayPanel {
    private final NetoSalvagingScript script;

    @Inject
    NetoSalvagingOverlay(NetoSalvagingPlugin plugin, NetoSalvagingScript script) {
        super(plugin);
        this.script = script;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Inject
    private NetoBreakManager breakManager;
    @Inject
    private NetoWorldHopManager worldHopManager;
    @Inject
    private NetoRuntimeDisable runtimeDisable;

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.getChildren().clear();
            panelComponent.setPreferredSize(new Dimension(200, 250));

            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("Neto Salvaging " + NetoSalvagingPlugin.version).color((Color.cyan)).build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("World Hop In:").right(worldHopManager.getWorldHopDisplay()).build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Break In:").right(breakManager.getBreakInDisplay()).build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Shutdown in:").right(runtimeDisable.getShutdownInDisplay()).build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Time Running:").right(TimeUtils.getFormattedDurationBetween(script.getStartTime(), Instant.now())).build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Status:").right(Microbot.status).build());
        } catch (Exception ex) {
            Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
        }
        return super.render(graphics);
    }
}
