package net.runelite.client.plugins.microbot.netofletching.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NetoFletchingMaterial
{
    LOG("Log"),
    WOOD("Wood"),
    OAK("Oak"),
    WILLOW("Willow"),
    MAPLE("Maple"),
    YEW("Yew"),
    MAGIC("Magic"),
    REDWOOD("Redwood");

    private final String name;

    public String getLogItemName() {
        return this == LOG ? "Logs" : name + " logs";
    }

    @Override
    public String toString()
    {
        return name;
    }
}
