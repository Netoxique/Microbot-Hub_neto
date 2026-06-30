package net.runelite.client.plugins.microbot.netoalching;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class NetoAlchingOverlay extends OverlayPanel {

    private final NetoAlchingScript script;

    @Inject
    NetoAlchingOverlay(NetoAlchingPlugin plugin, NetoAlchingScript script) {
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
                .text("Neto Alching v" + NetoAlchingPlugin.version)
                .color(Color.GREEN)
                .build());
        panelComponent.getChildren().add(LineComponent.builder().build());
        panelComponent.getChildren().add(LineComponent.builder()
                .left("State:")
                .right(script.getState().toString())
                .build());
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Status:")
                .right(Microbot.status)
                .build());
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Profit:")
                .right(NetoAlchingScript.formatProfit(script.getTotalProfit()))
                .build());
        return super.render(graphics);
    }
}
