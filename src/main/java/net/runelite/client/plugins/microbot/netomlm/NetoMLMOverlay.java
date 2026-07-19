package net.runelite.client.plugins.microbot.netomlm;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.netomlm.NetoMLMPlugin;
import net.runelite.client.plugins.microbot.netomlm.NetoMLMScript;
import static net.runelite.client.plugins.microbot.netomlm.NetoMLMScript.status;
import net.runelite.client.plugins.microbot.netomlm.enums.MLMMiningSpot;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

@Slf4j
public class NetoMLMOverlay extends OverlayPanel {
    @Inject
    NetoMLMOverlay(NetoMLMPlugin plugin) {
        super(plugin);
        setPosition(OverlayPosition.TOP_LEFT);
        setSnappable(true);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {

            panelComponent.setPreferredSize(new Dimension(275, 900));
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("\uD83E\uDD86 Neto MLM \uD83E\uDD86")
                    .color(Color.ORANGE)
                    .build());


            if (Rs2AntibanSettings.devDebug)
                Rs2Antiban.renderAntibanOverlayComponents(panelComponent);

            addEmptyLine();


            if (NetoMLMScript.miningSpot != MLMMiningSpot.IDLE) {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Mining Location: " + NetoMLMScript.miningSpot.name())
                        .build());
            }

            // Keep the state row directly below the mining location.

            panelComponent.getChildren().add(LineComponent.builder()
                    .left(status.toString())
                    .right("Version: " + NetoMLMPlugin.version)
                    .build());
        } catch (Exception ex) {
            log.error("Error rendering Neto MLM overlay: ", ex);
        }
        return super.render(graphics);
    }

    private void addEmptyLine() {
        panelComponent.getChildren().add(LineComponent.builder().build());
    }
}
