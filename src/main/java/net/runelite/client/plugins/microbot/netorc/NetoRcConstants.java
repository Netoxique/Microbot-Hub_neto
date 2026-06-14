package net.runelite.client.plugins.microbot.netorc;

import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;

final class NetoRcConstants {
    static final WorldPoint FEROX_POOL = new WorldPoint(3129, 3636, 0);
    static final WorldPoint MONASTERY_FAIRY_RING = new WorldPoint(2656, 3230, 0);
    static final WorldPoint CAVE_FAIRY_RING = new WorldPoint(3447, 9824, 0);
    static final WorldPoint GUILD_SPIRIT_TREE = new WorldPoint(1252, 3749, 0);
    static final WorldPoint BLOOD_CAVE_5 = new WorldPoint(3560, 9814, 0);

    static final int FEROX_POOL_OBJECT = 39651;
    static final int MONASTERY_REGION = 10290;
    static final int GUILD_SPIRIT_TREE_OBJECT = ObjectID.FARMING_SPIRIT_TREE_PATCH_5;

    static final int[] POH_POOL_OBJECTS = {29241, 29240, 29239, 29238, 29237};

    private NetoRcConstants() {
    }
}
