package net.runelite.client.plugins.microbot.lunarplankmake;

import net.runelite.client.plugins.microbot.shared.session.NetoBreakManager;
import net.runelite.client.plugins.microbot.shared.session.NetoRuntimeDisable;
import net.runelite.client.plugins.microbot.shared.session.NetoWorldHopManager;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class LunarPlankMakeOverlay extends OverlayPanel {

    @Inject
    private NetoBreakManager breakManager;
    @Inject
    private NetoWorldHopManager worldHopManager;
    @Inject
    private NetoRuntimeDisable runtimeDisable;

    @Inject
    LunarPlankMakeOverlay(LunarPlankMakePlugin plugin) {
        super(plugin);
        setPosition(OverlayPosition.ABOVE_CHATBOX_RIGHT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        panelComponent.setPreferredSize(new Dimension(200, 300));
        panelComponent.getChildren().add(TitleComponent.builder()
                .text("Plank Make " + LunarPlankMakePlugin.version)
                .color(Color.YELLOW)
                .build());

        // Update to display the combined message
        panelComponent.getChildren().add(LineComponent.builder()
                .left(LunarPlankMakeScript.combinedMessage)
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Inventories:").right(worldHopManager.getTripsDisplay()).build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Break In:").right(breakManager.getBreakInDisplay()).build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Shutdown in:").right(runtimeDisable.getShutdownInDisplay()).build());

        return super.render(graphics);
    }
}