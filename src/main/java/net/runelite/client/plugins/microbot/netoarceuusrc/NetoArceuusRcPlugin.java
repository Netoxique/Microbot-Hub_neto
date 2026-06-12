package net.runelite.client.plugins.microbot.netoarceuusrc;

import com.google.inject.Provides;
import lombok.Getter;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerPlugin;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.time.Instant;

@PluginDescriptor(
        name = "Neto Arceuus RC",
        description = "Runecrafting at Arceuus",
        authors = { "Neto" },
        version = NetoArceuusRcPlugin.version,
        minClientVersion = "1.9.9.1",
        tags = {"runecrafting", "blood rune", "soul rune" ,"arceuus", "microbot"},
        iconUrl = "https://chsami.github.io/Microbot-Hub/NetoArceuusRcPlugin/assets/icon.png",
        cardUrl = "https://chsami.github.io/Microbot-Hub/NetoArceuusRcPlugin/assets/card.png",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
public class NetoArceuusRcPlugin extends Plugin {

    public static final String version = "1.0.6";

    @Getter
    @Inject
    private NetoArceuusRcConfig config;

    @Provides
    NetoArceuusRcConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(NetoArceuusRcConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private NetoArceuusRcOverlay netoArceuusRcOverlay;
    @Getter
    private Instant startTime;
    @Getter
    @Inject
    NetoArceuusRcScript netoArceuusRcScript;

    @Override
    protected void startUp() {
        startTime = Instant.now();
        if (overlayManager != null) {
            overlayManager.add(netoArceuusRcOverlay);
        }
        netoArceuusRcScript.run(config);
    }

    protected void shutDown() {
        startTime = null;
        netoArceuusRcScript.shutdown();
        overlayManager.remove(netoArceuusRcOverlay);
    }

    public boolean isBreakHandlerEnabled() {
        return Microbot.isPluginEnabled(BreakHandlerPlugin.class);
    }
}
