package net.runelite.client.plugins.microbot.netolunartanner;

import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;
import java.time.Instant;

@PluginDescriptor(
        name = "Neto Lunar Tanner",
        description = "Tans hides on the lunar spellbook",
        tags = {"magic", "moneymaking", "neto"},
        authors = { "Neto" },
        version = NetoLunarTannerPlugin.version,
        minClientVersion = "2.0.13",
        cardUrl = "",
        iconUrl = "",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class NetoLunarTannerPlugin extends Plugin {
    public static final String version = "1.0.0";
    @Getter
    private Instant startTime;

    @Inject
    private NetoLunarTannerConfig config;

    @Provides
    NetoLunarTannerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(NetoLunarTannerConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private NetoLunarTannerOverlay netoLunarTannerOverlay;

    @Inject
    NetoLunarTannerScript netoLunarTannerScript;


    @Override
    protected void startUp() throws AWTException {
        log.info("Starting up NetoLunarTannerPlugin");
        startTime = Instant.now();
        if (overlayManager != null) {
            overlayManager.add(netoLunarTannerOverlay);
        }
        netoLunarTannerScript.run(config);
    }

    @Override
    protected void shutDown() {
        log.info("Shutting down NetoLunarTannerPlugin");
        netoLunarTannerScript.shutdown();
        overlayManager.remove(netoLunarTannerOverlay);
    }

    public boolean isBreakHandlerEnabled() {
        return Microbot.isPluginEnabled(net.runelite.client.plugins.microbot.breakhandler.BreakHandlerPlugin.class);
    }
}
