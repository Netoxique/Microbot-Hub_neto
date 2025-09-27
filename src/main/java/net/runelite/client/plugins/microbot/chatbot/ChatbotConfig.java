package com.yourorg.microbot.plugins.chatauto;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

import java.util.LinkedHashSet;
import java.util.Set;

@ConfigGroup("chatAutoReply")
public interface ChatAutoReplyConfig extends Config {

    @ConfigItem(keyName = "apiKey", name = "OpenAI API Key", description = "sk-... key")
    default String apiKey() { return ""; }

    @ConfigItem(keyName = "modelId", name = "Model", description = "e.g., gpt-4.1-mini")
    default String modelId() { return "gpt-4.1-mini"; }

    @ConfigItem(keyName = "basePrompt", name = "Base Prompt", description = "Persona/tone")
    default String basePrompt() {
        return "Reply like a polite OSRS player. Keep it casual and short.";
    }

    @ConfigItem(keyName = "temperature", name = "Temperature")
    default double temperature() { return 0.4; }

    @ConfigItem(keyName = "maxReplyTokens", name = "Max tokens")
    default int maxReplyTokens() { return 60; }

    @ConfigItem(keyName = "maxLineLength", name = "Max characters")
    default int maxLineLength() { return 120; }

    @ConfigItem(keyName = "trackAll", name = "Track everyone", description = "Track conversations even without mentions")
    default boolean trackAll() { return true; }

    @ConfigItem(keyName = "blacklist", name = "Blacklist (comma-separated)")
    default Set<String> blacklist() {
        Set<String> s = new LinkedHashSet<>();
        s.add("gold");
        s.add("sell gp");
        s.add("rwt");
        s.add("script");
        s.add("bot");
        return s;
    }
}
