package net.runelite.client.plugins.microbot.shared.session;

import lombok.Getter;
import net.runelite.http.api.worlds.WorldRegion;

@Getter
public enum NetoWorldHopRegion {
    ALL("All", null),
    UNITED_STATES_OF_AMERICA("United States of America", WorldRegion.UNITED_STATES_OF_AMERICA),
    UNITED_KINGDOM("United Kingdom", WorldRegion.UNITED_KINGDOM),
    AUSTRALIA("Australia", WorldRegion.AUSTRALIA),
    GERMANY("Germany", WorldRegion.GERMANY),
    BRAZIL("Brazil", WorldRegion.BRAZIL);

    private final String name;
    private final WorldRegion worldRegion;

    NetoWorldHopRegion(String name, WorldRegion worldRegion) {
        this.name = name;
        this.worldRegion = worldRegion;
    }

    @Override
    public String toString() {
        return name;
    }
}
