package net.runelite.client.plugins.microbot.netodegrimer;

import com.google.inject.Provides;
import java.awt.AWTException;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;

@PluginDescriptor(
    name = PluginConstants.DEFAULT_PREFIX + "Neto Degrimer",
    description = "Automatically cleans grimy herbs using the Degrime spell.",
    tags = {"herblore", "magic", "microbot"},
    authors = {"Neto"},
    version = NetoDegrimerPlugin.VERSION,
    minClientVersion = "1.9.8",
    enabledByDefault = PluginConstants.DEFAULT_ENABLED,
    isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class NetoDegrimerPlugin extends Plugin
{
    static final String VERSION = "1.0.0";

    @Inject
    private NetoDegrimerConfig config;

    @Inject
    private NetoDegrimerScript script;

    @Provides
    NetoDegrimerConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(NetoDegrimerConfig.class);
    }

    @Override
    protected void startUp() throws AWTException
    {
        script.run(config);
    }

    @Override
    protected void shutDown()
    {
        script.shutdown();
    }
}
