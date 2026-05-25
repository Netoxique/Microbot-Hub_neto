package net.runelite.client.plugins.microbot.netocooking;

import net.runelite.api.GameObject;
import net.runelite.api.ItemID;
import net.runelite.api.ObjectID;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class NetoCookingScript extends Script {

    private GameObject currentRange;

    public boolean run() {
        Microbot.enableAutoRunOn = false;

        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyCookingSetup();
        Rs2Antiban.setActivity(Activity.COOKING_RAW_KARAMBWAN);
        Rs2AntibanSettings.simulateMistakes = false;

        Rs2Camera.setZoom(1070);

        // Find range at startup
        currentRange = findRange();

        if (currentRange == null) {
            Microbot.showMessage("No range found, shutting down.");
            shutdown();
        }

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run() || !Microbot.isLoggedIn()) return;

                if (Rs2Inventory.hasItem(ItemID.RAW_KARAMBWAN)) {
                    cook();
                } else {
                    bank();
                }
            } catch (Exception ex) {
                Microbot.log(ex.getMessage());
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    private void bank() {
        if (!Rs2Bank.openBank()) {
            return;
        }

        if (!Rs2Inventory.isEmpty()) {
            Rs2Bank.depositAll();
        }
        sleepGaussian(300,100);

        if (!Rs2Equipment.isWearing(ItemID.COOKING_GAUNTLETS)) {
            if (Rs2Inventory.hasItem(ItemID.COOKING_GAUNTLETS)) {
                Rs2Inventory.interact(ItemID.COOKING_GAUNTLETS, "Wear");
                sleepUntil(() -> Rs2Equipment.isWearing(ItemID.COOKING_GAUNTLETS));
            } else if (Rs2Bank.hasItem(ItemID.COOKING_GAUNTLETS)) {
                Rs2Bank.withdrawOne(ItemID.COOKING_GAUNTLETS);
                sleepUntil(() -> Rs2Inventory.hasItem(ItemID.COOKING_GAUNTLETS));
                Rs2Inventory.interact(ItemID.COOKING_GAUNTLETS, "Wear");
                sleepUntil(() -> Rs2Equipment.isWearing(ItemID.COOKING_GAUNTLETS));
            }
            Rs2Bank.depositAll();
            sleepGaussian(900,300);
        }

        if (Rs2Bank.hasItem(ItemID.RAW_KARAMBWAN)) {
            Rs2Bank.withdrawAll(ItemID.RAW_KARAMBWAN);
        } else {
            Microbot.showMessage("Out of raw karambwans");
            shutdown();
        }

        Rs2Bank.closeBank();
    }

    private void cook() {

        sleepUntil(() -> Rs2Inventory.hasItem(ItemID.RAW_KARAMBWAN), 3000);
        sleepGaussian(300,150);

        GameObject range = currentRange;
        if (!isRangeValid(range)) {
            range = findRange();
            if (range == null) {
                Microbot.showMessage("No range found, shutting down.");
                shutdown();
                return;
            }
            currentRange = range;
        }

        Microbot.status = "Cooking karambwans";

        AtomicBoolean keepPressing = new AtomicBoolean(true);
        Thread spacePresser = new Thread(() -> {
            try {
                while (keepPressing.get()) {
                    Rs2Keyboard.keyHold(KeyEvent.VK_SPACE);
                    Thread.sleep(45);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "karambwan-space-presser");
        spacePresser.start();

        Rs2Antiban.setActivityIntensity(ActivityIntensity.EXTREME);
        Rs2ItemModel lastKarambwan;
        while ((lastKarambwan = Rs2Inventory.getLast(ItemID.RAW_KARAMBWAN)) != null) {
            Rs2Inventory.interact(lastKarambwan, "Use");
            Rs2GameObject.interact(range, "Use");
            sleepGaussian(400,100);
        }
        Rs2Antiban.setActivityIntensity(ActivityIntensity.LOW);

        keepPressing.set(false);
        try {
            spacePresser.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        Rs2Keyboard.keyRelease(KeyEvent.VK_SPACE);
    }

    private GameObject findRange() {
        int[] rangeIds = new int[]{
                ObjectID.CLAY_OVEN_21302, // Hosidius / Generic
                ObjectID.FIRE_43475, // Rogue's Den
                ObjectID.RANGE_7183, // Cook's Guild / Generic
                31631, // Myth's Guild
        };

        List<GameObject> objects = Rs2GameObject.getGameObjects();
        for (GameObject obj : objects) {
            if (Arrays.stream(rangeIds).anyMatch(id -> id == obj.getId())) {
                return obj;
            }
        }
        return null;
    }

    private boolean isRangeValid(GameObject range) {
        if (range == null || range.getWorldLocation() == null) {
            return false;
        }

        for (GameObject obj : Rs2GameObject.getGameObjects()) {
            if (obj != null
                    && obj.getId() == range.getId()
                    && obj.getWorldLocation().equals(range.getWorldLocation())) {
                return true;
            }
        }
        return false;
    }
}
