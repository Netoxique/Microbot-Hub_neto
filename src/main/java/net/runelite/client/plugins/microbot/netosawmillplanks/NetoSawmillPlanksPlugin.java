package net.runelite.client.plugins.microbot.netosawmillplanks;

import com.google.inject.Provides;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginConstants.DEFAULT_PREFIX + "Neto Sawmill Planks",
        description = "Automates mahogany planks at the Earth Altar sawmill",
        tags = {"sawmill", "planks", "mahogany"},
        version = NetoSawmillPlanksPlugin.version,
        minClientVersion = "2.6.5",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
public class NetoSawmillPlanksPlugin extends Plugin {
    public static final String version = "1.0.0";
    @Inject
    private NetoSawmillPlanksScript netoSawmillPlanksScript;

    @Override
    protected void startUp() throws Exception {
        netoSawmillPlanksScript.run();
    }

    @Override
    protected void shutDown() throws Exception {
        netoSawmillPlanksScript.shutdown();
    }
}
