package net.runelite.client.plugins.microbot.netogemstones;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;
import java.time.Instant;
import net.runelite.client.plugins.microbot.util.misc.TimeUtils;


@PluginDescriptor(
        name = "Neto Gemstones",
        description = "Mines gemstone rocks and banks them.",
        tags = {"mining", "gemstone", "neto"},
        enabledByDefault = false
)
@Slf4j
public class NetoGemstonesPlugin extends Plugin {
    @Inject
    private NetoGemstonesConfig config;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private NetoGemstonesOverlay overlay;
    @Inject
    private NetoGemstonesScript script;

    private Instant scriptStartTime;

    @Provides
    NetoGemstonesConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(NetoGemstonesConfig.class);
    }

    @Override
    protected void startUp() throws AWTException {
        scriptStartTime = Instant.now();
        if (overlayManager != null) {
            overlayManager.add(overlay);
        }
        script.run(config);
    }

    @Override
    protected void shutDown() {
        script.shutdown();
        overlayManager.remove(overlay);
    }

    protected String getTimeRunning() {
        return scriptStartTime != null ? TimeUtils.getFormattedDurationBetween(scriptStartTime, Instant.now()) : "";
    }
}
