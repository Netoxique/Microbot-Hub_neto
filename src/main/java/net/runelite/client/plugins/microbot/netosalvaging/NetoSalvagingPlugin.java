package net.runelite.client.plugins.microbot.netosalvaging;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = "Neto Salvaging",
        description = "Deposits items, withdraws salvage items, and sorts them at the Salvaging station.",
        tags = {"neto", "salvaging", "sailing"},
        authors = {"Neto"},
        version = NetoSalvagingPlugin.version,
        minClientVersion = "2.0.0",
        enabledByDefault = false,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class NetoSalvagingPlugin extends Plugin {
    public static final String version = "1.0.0";

    @Inject
    private NetoSalvagingConfig config;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private NetoSalvagingOverlay overlay;

    @Inject
    private NetoSalvagingScript script;

    @Provides
    NetoSalvagingConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(NetoSalvagingConfig.class);
    }

    @Override
    protected void startUp() throws AWTException {
        Microbot.pauseAllScripts.compareAndSet(true, false);
        if (overlayManager != null) {
            overlayManager.add(overlay);
        }
        script.run();
    }

    @Override
    protected void shutDown() {
        script.shutdown();
        if (overlayManager != null) {
            overlayManager.remove(overlay);
        }
    }
}
