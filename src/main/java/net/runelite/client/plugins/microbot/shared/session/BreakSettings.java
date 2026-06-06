package net.runelite.client.plugins.microbot.shared.session;

public interface BreakSettings {
    boolean enableBreaks();

    int minPlaytime();

    int maxPlaytime();

    int minBreak();

    int maxBreak();
}
