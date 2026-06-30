package net.runelite.client.plugins.microbot.netogeseller;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("neto-geseller")
public interface NetoGeSellerConfig extends Config {
    @ConfigSection(
            name = "General Settings",
            description = "General settings for the GE Seller",
            position = 0
    )
    String generalSettings = "generalSettings";

    @ConfigItem(
            keyName = "itemsToSell",
            name = "Items to Sell",
            description = "Comma separated list of items to sell. Format: ItemName:KeepAmount (e.g. Magic log:100, Coal:500, Rune bar, Teak logs:>200)",
            position = 1,
            section = generalSettings
    )
    default String itemsToSell() {
        return "Magic log:100, Coal:500, Rune bar";
    }

    @ConfigItem(
            keyName = "hotkey",
            name = "Insta-Sell Hotkey",
            description = "The hotkey configured in Flipping Utilities for setting price to low/insta-sell (usually 'n')",
            position = 2,
            section = generalSettings
    )
    default String hotkey() {
        return "n";
    }
}
