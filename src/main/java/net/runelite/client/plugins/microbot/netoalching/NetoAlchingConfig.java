package net.runelite.client.plugins.microbot.netoalching;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("neto-alching")
public interface NetoAlchingConfig extends Config {

    @ConfigItem(
            keyName = "alchItems",
            name = "Alch Items",
            description = "Comma separated list of items to alch (e.g. Rune dagger, Adamant platebody, Maple longbow (u))",
            position = 1
    )
    default String alchItems() {
        return "Rune dagger, Adamant platebody, Maple longbow (u)";
    }
}
