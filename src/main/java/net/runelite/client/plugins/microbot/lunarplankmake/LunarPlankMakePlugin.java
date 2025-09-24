package net.runelite.client.plugins.microbot.lunarplankmake;

import java.awt.AWTException;

import javax.inject.Inject;

import com.google.inject.Provides;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.mouse.VirtualMouse;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
        name = "Neto Lunar Plank Make",
        description = "lunar plank maker",
        tags = {"magic", "moneymaking"},
        enabledByDefault = false,
        minClientVersion = "2.0.0"
)
@Slf4j
public class LunarPlankMakePlugin extends Plugin {
    @Inject
    private LunarPlankMakeConfig config;

    @Provides
    LunarPlankMakeConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(LunarPlankMakeConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private LunarPlankMakeOverlay LunarPlankMakeOverlay;

    @Inject
    LunarPlankMakeScript LunarPlankMakeScript;

    @Override
    protected void startUp() throws AWTException {
        log.info("Starting up LunarPlankMakePlugin");
        if (overlayManager != null) {
            overlayManager.add(LunarPlankMakeOverlay);
        }
        LunarPlankMakeScript.run(config);
    }

    @Override
    protected void shutDown() {
        log.info("Shutting down LunarPlankMakePlugin");
        LunarPlankMakeScript.shutdown();
        overlayManager.remove(LunarPlankMakeOverlay);
    }
}
