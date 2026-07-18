package net.runelite.client.plugins.microbot.netobirdhouseruns;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;


@PluginDescriptor(
        name = "Neto Birdhouse Runner",
        description = "Does a birdhouse run",
        tags = {"NetoBirdhouseRuns", "neto"},
        authors = {"Neto"},
        version = NetoBirdhouseRunsPlugin.version,
        minClientVersion = "2.1.0",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL,
        iconUrl = "https://chsami.github.io/Microbot-Hub/NetoBirdhouseRunsPlugin/assets/icon.jpg",
        cardUrl = "https://chsami.github.io/Microbot-Hub/NetoBirdhouseRunsPlugin/assets/card.jpg"
)
@Slf4j
public class NetoBirdhouseRunsPlugin extends Plugin {
    static final String version = "1.1.5";
    @Provides
    NetoBirdhouseRunsConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(NetoBirdhouseRunsConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private NetoBirdhouseRunsOverlay netoBirdhouseRunsOverlay;
    @Inject
    NetoBirdhouseRunsScript netoBirdhouseRunsScript;

    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(netoBirdhouseRunsOverlay);
        }
        netoBirdhouseRunsScript.run();
    }

    protected void shutDown() {
        netoBirdhouseRunsScript.shutdown();
        overlayManager.remove(netoBirdhouseRunsOverlay);
    }
}
