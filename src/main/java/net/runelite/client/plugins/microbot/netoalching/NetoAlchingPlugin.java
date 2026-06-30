package net.runelite.client.plugins.microbot.netoalching;

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
        name = "Neto Alching",
        description = "High-alchemy bot that automatically prepares, withdraws as notes, and alchs configured items",
        tags = {"neto", "alch", "alchemy", "magic"},
        authors = {"Neto"},
        version = NetoAlchingPlugin.version,
        minClientVersion = "2.0.0",
        enabledByDefault = false,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class NetoAlchingPlugin extends Plugin {
    public static final String version = "1.0.0";

    @Inject
    private NetoAlchingConfig config;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private NetoAlchingOverlay overlay;

    @Inject
    private NetoAlchingScript script;

    @Provides
    NetoAlchingConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(NetoAlchingConfig.class);
    }

    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(overlay);
        }
        script.run(config);
    }

    @Override
    protected void shutDown() {
        script.shutdown();
        if (overlayManager != null) {
            overlayManager.remove(overlay);
        }
    }
}
