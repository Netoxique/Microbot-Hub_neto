package net.runelite.client.plugins.microbot.netokarambwans;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.NpcID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.runecrafting.chillRunecraft.States;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.netokarambwans.KarambwanInfo.botStatus;
import static net.runelite.client.plugins.microbot.netokarambwans.KarambwanInfo.states;

@Slf4j
public class KarambwansScript extends Script {
    public static double version = 1.2;
    private final WorldPoint fishingPoint = new WorldPoint(2899, 3118, 0);
    private final WorldPoint chasmBank = new WorldPoint(1481, 3649, 0);
    private final WorldPoint baitPoint = new WorldPoint(2804, 3006, 0);
    // Using a more generic bank location that Microbot can find easily.
    // This can be any bank, Rs2Bank.walkToBank() will find the nearest one.

    public boolean run(KarambwansConfig config) {
        Microbot.enableAutoRunOn = true;

        Rs2Camera.setPitch(512);
        Rs2Camera.setZoom(230);
        Rs2Camera.setYaw(0);
        sleepGaussian(600, 200);

        Rs2Antiban.setActivity(Activity.CATCHING_RAW_KARAMBWAN);
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;

                switch (botStatus) {
                    case FISHING:
                        fishingLoop();
                        break;
                    case WALKING_TO_BANK:
                        walkToBank();
                        botStatus = states.BANKING;
                        break;
                    case BANKING:
                        useBank();
                        botStatus = states.WALKING_TO_FISH;
                        break;
                    case WALKING_TO_FISH:
                        walkToFish();
                        botStatus = states.FISHING;
                        break;
                    case GETTING_BAIT:
                        setupBaitFishing();
                        botStatus = states.FISHING_BAIT;
                        break;
                    case FISHING_BAIT:
                        baitingLoop(config);
                        break;
                }
            } catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }

    private void fishingLoop() {
        if (Rs2Inventory.isFull()) {
            botStatus = states.WALKING_TO_BANK;
            return;
        }
        if (!Rs2Inventory.contains(ItemID.TBWT_RAW_KARAMBWANJI)) {
            botStatus = states.GETTING_BAIT;
            return;
        }
        if (!Rs2Player.isInteracting() && !Rs2Player.isAnimating()) {
            interactWithFishingSpot();
            Rs2Player.waitForAnimation();
        }
    }

    private void walkToBank() {
        Rs2Walker.walkTo(chasmBank,3);
//        if (Rs2Bank.walkToBank()) {
//            sleepUntil(() -> Rs2Bank.isNearBank(10));
//        }
    }

    private void useBank() {
        Rs2Bank.openBank();
        sleepUntil(Rs2Bank::isOpen);
        sleepGaussian(600,200);
        Rs2Bank.depositAll(ItemID.TBWT_RAW_KARAMBWAN);
        sleepGaussian(600,200);
        Rs2Bank.depositAll(ItemID.NET);
        sleepGaussian(600,200);
        Rs2Bank.depositAll("scroll"); // Handles all tiers of clue scrolls
        sleepGaussian(600,200);
        if (Rs2Inventory.hasItem(ItemID.FISH_BARREL_OPEN) || Rs2Inventory.hasItem(ItemID.FISH_BARREL_CLOSED)) {
            Rs2Bank.emptyFishBarrel();
        }
        Rs2Bank.closeBank();
    }

    private void interactWithFishingSpot() {
        Rs2Npc.interact(NpcID._0_45_48_KARAMBWAN, "Fish");
    }

    private void setupBaitFishing() {
        Rs2Walker.walkTo(chasmBank,3);
//        if (!Rs2Bank.isNearBank(10)) {
//            Rs2Bank.walkToBank();
//            sleepUntil(() -> Rs2Bank.isNearBank(10));
//        }
        Rs2Bank.openBank();
        sleepUntil(Rs2Bank::isOpen);
        Rs2Bank.depositAll(ItemID.TBWT_RAW_KARAMBWAN);
        sleepGaussian(600,200);
        Rs2Bank.withdrawItem(ItemID.NET);
        Rs2Walker.walkTo(baitPoint);
    }

    private void baitingLoop(KarambwansConfig config) {
        if (Rs2Inventory.itemQuantity(ItemID.TBWT_RAW_KARAMBWANJI) >= config.karambwanjiToFish()) {
            Rs2Inventory.dropAll("Raw shrimps");
            botStatus = states.WALKING_TO_FISH;
            return;
        }

        if (Rs2Inventory.isFull()) {
            Rs2Inventory.dropAll("Raw shrimps");
        }

        if (!Rs2Player.isInteracting() && !Rs2Player.isAnimating()) {
            Rs2Npc.interact("Fishing spot", "Net"); // Generic name interaction is fine here
            Rs2Player.waitForAnimation();
            sleepGaussian(1500,1000);
        }
    }

    private void walkToFish() {
        Rs2Walker.walkTo(fishingPoint, 10);
        Rs2Player.waitForWalking();
    }
}