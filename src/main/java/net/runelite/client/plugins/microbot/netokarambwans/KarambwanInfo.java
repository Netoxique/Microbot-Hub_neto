package net.runelite.client.plugins.microbot.netokarambwans;

public class KarambwanInfo {
    public static states botStatus;


    public enum states {
        FISHING,
        WALKING_TO_BANK,
        BANKING,
        WALKING_TO_FISH,
        GETTING_BAIT,
        FISHING_BAIT
    }

}