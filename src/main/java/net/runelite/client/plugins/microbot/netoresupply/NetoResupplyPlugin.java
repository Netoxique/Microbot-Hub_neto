package net.runelite.client.plugins.microbot.netoresupply;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;

import javax.inject.Inject;

@PluginDescriptor(
        name = "Neto Resupply",
        description = "Maintains configured bank supplies by buying deficits from the Grand Exchange.",
        tags = {"neto", "resupply", "grand exchange", "bank"},
        authors = {"Neto"},
        version = NetoResupplyPlugin.version,
        minClientVersion = "2.6.13",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class NetoResupplyPlugin extends Plugin {
    public static final String version = "1.0.3";

    @Inject
    private NetoResupplyConfig config;
    @Inject
    private NetoResupplyScript script;

    @Provides
    NetoResupplyConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(NetoResupplyConfig.class);
    }

    @Override
    protected void startUp() {
        script.run(config, this);
    }

    @Override
    protected void shutDown() {
        script.shutdown();
    }
}
