package net.runelite.client.plugins.microbot.netocooking;

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
        name = "Neto Cooking",
        description = "Cooks karambwans",
        tags = {"neto", "cooking", "karambwan"},
        authors = {"Neto"},
        version = "1.0.0",
        minClientVersion = "2.0.0",
        enabledByDefault = false,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class NetoCookingPlugin extends Plugin {
    public static final String version = "1.0.0";

    @Inject
    private NetoCookingConfig config;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private NetoCookingOverlay overlay;

    @Inject
    private NetoCookingScript script;

    @Provides
    NetoCookingConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(NetoCookingConfig.class);
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
        overlayManager.remove(overlay);
    }
}
