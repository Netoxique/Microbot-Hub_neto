package net.runelite.client.plugins.microbot.netoconstruction;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class NetoConstructionOverlay extends OverlayPanel {

    private final NetoConstructionPlugin plugin;

    @Inject
    public NetoConstructionOverlay(NetoConstructionPlugin plugin) {
        this.plugin = plugin;
        setPosition(OverlayPosition.TOP_LEFT);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        panelComponent.getChildren().clear();

        panelComponent.getChildren().add(TitleComponent.builder()
                .text("Neto Construction")
                .color(Color.YELLOW)
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("State:")
                .right(plugin.getState().toString())
                .build());

        return super.render(graphics);
    }
}
