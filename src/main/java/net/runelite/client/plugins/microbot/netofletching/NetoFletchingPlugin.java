package net.runelite.client.plugins.microbot.netofletching;

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
        name = "Neto Fletching",
        description = "Neto fletching plugin",
        authors = { "Neto" },
        version = NetoFletchingPlugin.version,
        minClientVersion = "2.0.0",
        tags = {"neto", "fletching", "microbot", "skills"},
        iconUrl = "https://chsami.github.io/Microbot-Hub/NetoFletchingPlugin/assets/icon.png",
        cardUrl = "https://chsami.github.io/Microbot-Hub/NetoFletchingPlugin/assets/card.png",
        enabledByDefault = false,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class NetoFletchingPlugin extends Plugin {

    public static final String version = "1.0.0";

    @Inject
    private NetoFletchingConfig config;

    @Provides
    NetoFletchingConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(NetoFletchingConfig.class);
    }
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private NetoFletchingOverlay netoFletchingOverlay;

    NetoFletchingScript netoFletchingScript;


    @Override
    protected void startUp() throws AWTException {
        Microbot.pauseAllScripts.compareAndSet(true, false);
        if (overlayManager != null) {
            overlayManager.add(netoFletchingOverlay);
        }
        netoFletchingScript = new NetoFletchingScript();
        netoFletchingScript.run(config);
    }

    protected void shutDown() {
        netoFletchingScript.shutdown();
        overlayManager.remove(netoFletchingOverlay);
    }
}
