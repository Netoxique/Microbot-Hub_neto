package net.runelite.client.plugins.microbot.neto.wildyescape;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;

import javax.inject.Inject;
import java.awt.AWTException;

@PluginDescriptor(
        name = "Neto WildyEscape",
        description = "Escapes the wilderness when a Phoenix necklace breaks",
        tags = {"microbot", "wildy", "escape"},
        enabledByDefault = false
)
@Slf4j
public class NetoWildyEscapePlugin extends Plugin {

    @Inject
    private NetoWildyEscapeScript script;

    @Override
    protected void startUp() throws AWTException {
        Microbot.pauseAllScripts.compareAndSet(true, false);
//        script.precalculatePath();
        script.run();
    }

    @Override
    protected void shutDown() {
        script.shutdown();
    }
}
