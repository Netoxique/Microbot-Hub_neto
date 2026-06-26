package net.runelite.client.plugins.microbot.netolunartanner;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class NetoLunarTannerOverlay extends OverlayPanel {

    @Inject
    NetoLunarTannerOverlay(NetoLunarTannerPlugin plugin)
    {
        super(plugin);
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        panelComponent.setPreferredSize(new Dimension(200, 300));
        panelComponent.getChildren().add(TitleComponent.builder()
                .text("Neto Tan Leather V" + NetoLunarTannerPlugin.version)
                .color(Color.GREEN)
                .build());

        // Update to display the combined message
        panelComponent.getChildren().add(LineComponent.builder()
                .left(NetoLunarTannerScript.combinedMessage)
                .build());

        return super.render(graphics);
    }
}
