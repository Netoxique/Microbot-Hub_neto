package net.runelite.client.plugins.microbot.netosailingsalv;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.netosailingsalv.features.salvaging.SalvagingHighlight;
import net.runelite.client.plugins.microbot.netosailingsalv.features.salvaging.SalvagingScript;
import net.runelite.client.plugins.microbot.netosailingsalv.features.trials.TrialsScript;
import net.runelite.client.plugins.microbot.netosailingsalv.features.trials.debug.BoatPathOverlay;
import net.runelite.client.plugins.microbot.netosailingsalv.features.trials.overlay.TrialRouteOverlay;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
	name = "Neto Sailing Salv.",
	description = "Microbot Sailing Plugin",
	tags = {"sailing"},
	authors = { "neto" },
	version = NetoSailingSalvPlugin.version,
	minClientVersion = "2.1.0",
	enabledByDefault = PluginConstants.DEFAULT_ENABLED,
	isExternal = PluginConstants.IS_EXTERNAL,
    cardUrl = "https://chsami.github.io/Microbot-Hub/NetoSailingSalvPlugin/assets/card.jpg",
    iconUrl = "https://chsami.github.io/Microbot-Hub/NetoSailingSalvPlugin/assets/icon.jpg"
)
@Slf4j
public class NetoSailingSalvPlugin extends Plugin {

	static final String version = "2.2.56";

    @Inject
    private NetoSailingSalvConfig config;
    @Provides
    NetoSailingSalvConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(NetoSailingSalvConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private NetoSailingSalvOverlay netoOverlay;
    @Inject
    private SalvagingHighlight netoSalvagingHighlight;

    @Inject
    private SalvagingScript netoSalvagingScript;

    @Inject
    private NetoSailingSalvScript netoScript;
    @Inject
    private TrialsScript netoTrialsScript;
    @Inject
    private BoatPathOverlay netoBoatPathOverlay;
    @Inject
    private TrialRouteOverlay netoTrialRouteOverlay;

    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(netoOverlay);
            overlayManager.add(netoSalvagingHighlight);
            overlayManager.add(netoBoatPathOverlay);
            overlayManager.add(netoTrialRouteOverlay);
        }
        netoSalvagingScript.register();
        netoTrialsScript.register();
        netoScript.run();
    }

    protected void shutDown() {
        netoScript.shutdown();
        netoTrialsScript.shutdown();
        netoSalvagingScript.unregister();
        netoTrialsScript.unregister();
        overlayManager.remove(netoOverlay);
        overlayManager.remove(netoSalvagingHighlight);
        overlayManager.remove(netoBoatPathOverlay);
        overlayManager.remove(netoTrialRouteOverlay);
    }
}
