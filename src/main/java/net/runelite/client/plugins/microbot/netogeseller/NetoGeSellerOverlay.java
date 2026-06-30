package net.runelite.client.plugins.microbot.netogeseller;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class NetoGeSellerOverlay extends OverlayPanel {

    private final NetoGeSellerScript script;

    @Inject
    NetoGeSellerOverlay(NetoGeSellerPlugin plugin, NetoGeSellerScript script) {
        super(plugin);
        this.script = script;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        panelComponent.getChildren().clear();
        panelComponent.setPreferredSize(new Dimension(200, 300));
        panelComponent.getChildren().add(TitleComponent.builder()
                .text("Neto GE Seller v" + NetoGeSellerPlugin.version)
                .color(Color.GREEN)
                .build());
        panelComponent.getChildren().add(LineComponent.builder().build());
        panelComponent.getChildren().add(LineComponent.builder()
                .left(Microbot.status)
                .build());
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Profit:")
                .right(NetoGeSellerScript.formatProfit(script.getTotalProfit()))
                .build());
        return super.render(graphics);
    }
}
