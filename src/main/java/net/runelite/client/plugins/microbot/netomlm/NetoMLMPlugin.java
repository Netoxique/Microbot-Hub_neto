package net.runelite.client.plugins.microbot.netomlm;

import com.google.inject.Provides;
import java.awt.AWTException;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(
	name = "Neto MLM",
	description = "A bot that mines paydirt in the motherlode mine",
	tags = {"paydirt", "mine", "motherlode", "mlm", "neto"},
	authors = { "Neto" },
	version = NetoMLMPlugin.version,
	minClientVersion = "1.9.8",
	iconUrl = "https://chsami.github.io/Microbot-Hub/NetoMLMPlugin/assets/icon.png",
	cardUrl = "https://chsami.github.io/Microbot-Hub/NetoMLMPlugin/assets/card.png",
	enabledByDefault = false,
	isExternal = PluginConstants.IS_EXTERNAL
)
public class NetoMLMPlugin extends Plugin {

	static final String version = "1.0.0";

    @Inject
    private NetoMLMConfig config;
    @Inject
    private OverlayManager overlayManager;

    @Inject
    private NetoMLMOverlay netoMLMOverlay;
    @Inject
    private NetoMLMScript netoMLMScript;

	@Getter
	private List<WorldPoint> blacklistedCrates = new ArrayList<>();

    @Provides
	NetoMLMConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(NetoMLMConfig.class);
    }

    @Override
    protected void startUp() throws AWTException {
		log.info("Starting Neto MLM plugin v{}", version);
        overlayManager.add(netoMLMOverlay);
        netoMLMScript.run();
		log.info("Neto MLM startup complete");
    }

    @Override
    public void shutDown() {
		log.info("Starting Neto MLM shutdown");
        netoMLMScript.shutdown();
        overlayManager.remove(netoMLMOverlay);
		blacklistedCrates.clear();
		log.info("Neto MLM shutdown complete");
    }
}
