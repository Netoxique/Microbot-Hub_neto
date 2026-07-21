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
            keyName = "offerTimeoutSeconds",
            name = "Repost Offer After",
            description = "Seconds to wait before aborting and reposting an active sell offer",
            position = 1,
            section = generalSettings
    )
    default int offerTimeoutSeconds() {
        return 60;
    }

    @ConfigItem(
            keyName = "itemsToSell",
            name = "Items to Sell",
            description = "Comma separated list of item names or exact item IDs to sell. Prefix an item with + to use the high-value price. Format: [+]ItemNameOrID:KeepAmount (e.g. +Magic log:100, 5075, Coal:500, Teak logs:>200)",
            position = 2,
            section = generalSettings
    )
    default String itemsToSell() {
        return "Magic log:100, Coal:500, Rune bar";
    }

    @ConfigItem(
            keyName = "hotkey",
            name = "Insta-Sell Hotkey",
            description = "The hotkey configured in Flipping Utilities for setting price to low/insta-sell (usually 'n')",
            position = 3,
            section = generalSettings
    )
    default String hotkey() {
        return "n";
    }

    @ConfigItem(
            keyName = "highValueHotkey",
            name = "High-Value Hotkey",
            description = "The hotkey configured in Flipping Utilities for setting price to high/insta-buy (usually 'j')",
            position = 4,
            section = generalSettings
    )
    default String highValueHotkey() {
        return "j";
    }
}
