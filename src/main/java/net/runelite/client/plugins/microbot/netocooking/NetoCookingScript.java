package net.runelite.client.plugins.microbot.netocooking;

import net.runelite.api.GameObject;
import net.runelite.api.ItemID;
import net.runelite.api.ObjectID;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
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

    public boolean run() {
        Microbot.enableAutoRunOn = false;

        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyCookingSetup();
        Rs2Antiban.setActivity(Activity.COOKING_RAW_KARAMBWAN);
        Rs2AntibanSettings.simulateMistakes = false;

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
        Rs2Bank.depositAll();

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
        GameObject range = findRange();
        if (range == null) {
            return;
        }

        sleepUntil(() -> Rs2Inventory.hasItem(ItemID.RAW_KARAMBWAN));
        sleepGaussian(300,150);

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

        Rs2ItemModel lastKarambwan;
        while ((lastKarambwan = Rs2Inventory.getLast(ItemID.RAW_KARAMBWAN)) != null) {
            Rs2Inventory.interact(lastKarambwan, "Use");
            Rs2GameObject.interact(range, "Use");
            sleepGaussian(400,100);
        }

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
                ObjectID.STOVE, ObjectID.STOVE_9086, ObjectID.STOVE_9087, ObjectID.STOVE_12269,
                ObjectID.GOBLIN_STOVE, ObjectID.GOBLIN_STOVE_25441, ObjectID.SIMPLE_STOVE,
                ObjectID.COOKING_STOVE, ObjectID.STOVE_51540, ObjectID.COOKING_RANGE,
                ObjectID.RANGE, ObjectID.COOKING_RANGE_4172, ObjectID.RANGE_7183,
                ObjectID.RANGE_7184, ObjectID.COOKING_RANGE_8750, ObjectID.RANGE_9682,
                ObjectID.RANGE_9736, ObjectID.RANGE_12102, ObjectID.RANGE_12611,
                ObjectID.STEEL_RANGE, ObjectID.STEEL_RANGE_13540, ObjectID.STEEL_RANGE_13541,
                ObjectID.FANCY_RANGE, ObjectID.FANCY_RANGE_13543, ObjectID.FANCY_RANGE_13544,
                ObjectID.COOKING_RANGE_16641, ObjectID.COOKING_RANGE_16893, ObjectID.RANGE_21792,
                ObjectID.COOKING_RANGE_22154, ObjectID.RANGE_22713, ObjectID.RANGE_22714,
                31631
        };

        List<GameObject> objects = Rs2GameObject.getGameObjects();
        for (GameObject obj : objects) {
            if (Arrays.stream(rangeIds).anyMatch(id -> id == obj.getId())) {
                return obj;
            }
        }
        return null;
    }
}
