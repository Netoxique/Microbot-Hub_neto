package net.runelite.client.plugins.microbot.netosuperglassmake;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = "Neto Superglass Make",
        description = "Neto's Superglass Make bot",
        tags = {"neto", "glassmake", "magic"},
        authors = {"Neto"},
        version = NetoSuperglassMakePlugin.version,
        minClientVersion = "2.0.13",
        cardUrl = "",
        iconUrl = "",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class NetoSuperglassMakePlugin extends Plugin {
    public static final String version = "1.0.0";
    @Inject
    private NetoSuperglassMakeConfig config;

    @Provides
    NetoSuperglassMakeConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(NetoSuperglassMakeConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private PluginManager pluginManager;
    @Inject
    private NetoSuperglassMakeOverlay netoSuperglassMakeOverlay;

    @Inject
    NetoSuperglassMakeScript netoSuperglassMakeScript;


    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(netoSuperglassMakeOverlay);
        }
        netoSuperglassMakeScript.run(config);
        NetoSuperglassMakeInfo.botStatus = NetoSuperglassMakeInfo.states.Starting;
    }

    protected void shutDown() {
        netoSuperglassMakeScript.shutdown();
        overlayManager.remove(netoSuperglassMakeOverlay);
    }
}
