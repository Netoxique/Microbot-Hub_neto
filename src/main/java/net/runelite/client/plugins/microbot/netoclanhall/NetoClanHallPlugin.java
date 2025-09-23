package net.runelite.client.plugins.microbot.netoclanhall;

import java.awt.AWTException;
import javax.inject.Inject;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;

@PluginDescriptor(
        name = "Neto ClanHall",
        description = "Automatically enters the clan hall portal when it appears",
        tags = {"clan", "portal", "entry"},
        authors = {"Neto"},
        version = NetoClanHallPlugin.VERSION,
        minClientVersion = "2.0.0",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
public class NetoClanHallPlugin extends Plugin
{
    static final String VERSION = "1.0.0";

    @Inject
    private NetoClanHallScript script;

    @Override
    protected void startUp() throws AWTException
    {
        script.run();
    }

    @Override
    protected void shutDown()
    {
        script.shutdown();
    }
}
