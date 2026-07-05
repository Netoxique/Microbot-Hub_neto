package net.runelite.client.plugins.microbot.netoconstruction;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.netoconstruction.enums.NetoConstructionState;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = "Neto Construction",
        description = "Neto's Construction plugin.",
        tags = {"neto", "skilling", "microbot", "construction"},
        authors = {"Neto"},
        version = NetoConstructionPlugin.version,
        minClientVersion = "2.0.13",
        enabledByDefault = false,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class NetoConstructionPlugin extends Plugin {
    public static final String version = "1.3.2";

    @Inject
    private NetoConstructionConfig config;

    @Provides
    NetoConstructionConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(NetoConstructionConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private NetoConstructionOverlay netoConstructionOverlay;

    private final NetoConstructionScript netoConstructionScript = new NetoConstructionScript();

    @Override
    protected void startUp() throws AWTException {
        Microbot.pauseAllScripts.compareAndSet(true, false);
        if (overlayManager != null) {
            overlayManager.add(netoConstructionOverlay);
        }
        netoConstructionScript.run(config);
    }

    @Override
    protected void shutDown() {
        netoConstructionScript.shutdown();
        if (overlayManager != null) {
            overlayManager.remove(netoConstructionOverlay);
        }
    }

    public NetoConstructionState getState() {
        return netoConstructionScript.getState();
    }
}
