package net.runelite.client.plugins.microbot.netosuperglassmake;

public class NetoSuperglassMakeInfo {
    public static states botStatus;


    public enum states {
        Starting,
        Prep,
        Banking,
        Glassblowing,
        Picking,

    }

    public enum items {
        Seaweed,
        GiantSeaweed
    }
}
