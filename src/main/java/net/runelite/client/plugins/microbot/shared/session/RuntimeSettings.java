package net.runelite.client.plugins.microbot.shared.session;

import net.runelite.client.config.ConfigItem;

public interface RuntimeSettings {

    @ConfigItem(
            keyName = "enableRuntime",
            name = "Enable",
            description = "Enable runtime limit.",
            position = 0,
            section = "Runtime"
    )
    default boolean enableRuntime() {
        return false;
    }

    @ConfigItem(
            keyName = "minRuntime",
            name = "Min. Runtime",
            description = "Minimum runtime before stopping the plugin (in minutes). 0 to disable.",
            position = 1,
            section = "Runtime"
    )
    default int minRuntime() {
        return 360;
    }

    @ConfigItem(
            keyName = "maxRuntime",
            name = "Max. Runtime",
            description = "Maximum runtime before stopping the plugin (in minutes). 0 to disable.",
            position = 2,
            section = "Runtime"
    )
    default int maxRuntime() {
        return 480;
    }
}
