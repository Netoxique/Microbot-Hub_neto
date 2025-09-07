package net.runelite.client.plugins.microbot.netokarambwans;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
//import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = "Neto Karambwans",
        description = "A plugin to fish karambwans",
        tags = {"blood", "rc", "rune", "wrath"},
        authors = {"Neoxic"},
        version = "1.0.0",
        minClientVersion = "2.0.0",
        enabledByDefault = false
//        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class KarambwansPlugin extends Plugin {
    @Inject
    private KarambwansConfig config;
    @Provides
    KarambwansConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(KarambwansConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private PluginManager pluginManager;
    @Inject
    private KarambwansOverlay karambwansOverlay;

    @Inject
    KarambwansScript karambwansScript;


    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(karambwansOverlay);
        }
        karambwansScript.run(config);
        KarambwanInfo.botStatus = config.STARTING_STATE();
    }

    protected void shutDown() {
        karambwansScript.shutdown();
        overlayManager.remove(karambwansOverlay);
    }
}
