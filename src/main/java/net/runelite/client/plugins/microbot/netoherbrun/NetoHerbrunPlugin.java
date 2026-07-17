package net.runelite.client.plugins.microbot.netoherbrun;

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
        name = "Neto Herb Runner",
        description = "Herb runner",
        tags = {"neto", "herb", "farming", "money making", "skilling"},
        authors = {"Neto"},
        version = NetoHerbrunPlugin.version,
        minClientVersion = "2.1.0",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class NetoHerbrunPlugin extends Plugin {
    public static final String version = "1.0.9";
    @Inject
    private NetoHerbrunConfig config;

    @Provides
    NetoHerbrunConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(NetoHerbrunConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private NetoHerbrunOverlay netoHerbrunOverlay;

    @Inject
    NetoHerbrunScript netoHerbrunScript;

    static String status;

    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(netoHerbrunOverlay);
        }
        netoHerbrunScript.run();
    }

    protected void shutDown() {
        netoHerbrunScript.shutdown();
        overlayManager.remove(netoHerbrunOverlay);
        status = null; // Reset status on shutdown
    }
}
