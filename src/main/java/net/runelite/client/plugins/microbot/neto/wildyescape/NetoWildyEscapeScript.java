package net.runelite.client.plugins.microbot.neto.wildyescape;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.storm.plugins.PlayerMonitor.PlayerMonitorPlugin;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.api.coords.WorldArea;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class NetoWildyEscapeScript extends Script {

    WorldPoint safeArea = new WorldPoint(2997, 3877, 0);
    WorldPoint gateArea = new WorldPoint(2998, 3931, 0);
    WorldPoint secondGate = new WorldPoint(2948, 3094, 0);

    int gate1 = 23552;
    int gate2 = 1727;

    WorldPoint southWestCorner = new WorldPoint(2991, 3936, 0);
    WorldPoint northEastCorner = new WorldPoint(3001, 3945, 0);

    int width  = (northEastCorner.getX() - southWestCorner.getX()) + 1; // 2995 - 2991 + 1 = 5
    int height = (northEastCorner.getY() - southWestCorner.getY()) + 1; // 3946 - 3936 + 1 = 11

    WorldArea rockArea = new WorldArea(southWestCorner, width, height);

    boolean path_calculated = false;
    public void precalculatePath() {
        if (!path_calculated){
            Rs2Walker.walkTo(safeArea);
            path_calculated = true;
        }
    }

    public boolean run() {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run()) return;
                if (!Microbot.isLoggedIn()) return;
                precalculatePath();
                if (!Rs2Equipment.isWearing("Phoenix necklace")) {
                    escape();
                }
            } catch (Exception e) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), e);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    private void escape() {
        // stop wilderness agility plugin
        Microbot.stopPlugin("net.runelite.client.plugins.microbot.wildernessagility.WildernessAgilityPlugin");
        // start player monitor plugin
        Microbot.startPlugin(PlayerMonitorPlugin.class);
        // equip necklace if found in inventory
        if (Rs2Inventory.hasItem("Phoenix necklace")) {
            while (!Rs2Equipment.isWearing("Phoenix necklace")) {
                Rs2Inventory.wield("Phoenix necklace");
                sleepUntil(() -> Rs2Equipment.isWearing("Phoenix necklace"), 600);
            }
        }
        // Check if player is in rock area
        boolean isInArea = rockArea.contains(Rs2Player.getWorldLocation());
        if (isInArea) {
            Rs2GameObject.interact(23640, "Climb"); // Climb rocks
            sleep(1200);
            sleepUntil(() -> !Rs2Player.isMoving(), 5000);
        }
        else {
            Rs2Walker.walkTo(gateArea, 4); // walk to gate
        }
        // open gate
        sleepUntilOnClientThread(() -> Rs2GameObject.getGameObject(23552) != null); // Wait for Gate
        Rs2GameObject.interact(gate1, "Open");
        // walk to safe location
        sleep(1200);
        sleepUntil(() -> !Rs2Player.isMoving());
        Rs2Walker.walkTo(safeArea, 20);
        // logout until successful
        while (Microbot.isLoggedIn()) {
            Rs2Player.logout();
            sleepUntil(() -> !Microbot.isLoggedIn(), 300);
        }
        // stop this script and plugin
        shutdown();
        Microbot.stopPlugin(NetoWildyEscapePlugin.class);
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }
}
