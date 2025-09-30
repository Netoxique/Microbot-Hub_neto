package net.runelite.client.plugins.microbot.netokarambwans;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.NpcID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
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
    private final WorldPoint baitPoint = new WorldPoint(2804, 3006, 0);


    public boolean run(KarambwansConfig config) {
        Microbot.enableAutoRunOn = true;

        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyRunecraftingSetup();
        Rs2Antiban.setActivity(Activity.GENERAL_FISHING);

        Rs2Camera.setZoom(230);
        Rs2Camera.setPitch(512);
        Rs2Camera.setYaw(0);

        sleepGaussian(600, 200);

        Rs2Antiban.setActivity(Activity.CATCHING_RAW_KARAMBWAN);
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;

                switch (botStatus) {
                    case PREP:
                        prep();
                        break;
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

    private void prep() {
        boolean hasStaffEquipped = Rs2Equipment.isWearing(ItemID.DRAMEN_STAFF) ||
                Rs2Equipment.isWearing(ItemID.LUNAR_MOONCLAN_LIMINAL_STAFF);
        boolean hasStaffInInv = Rs2Inventory.contains(ItemID.DRAMEN_STAFF) ||
                Rs2Inventory.contains(ItemID.LUNAR_MOONCLAN_LIMINAL_STAFF);
        boolean hasVessel = Rs2Inventory.contains(ItemID.TBWT_KARAMBWAN_VESSEL)
                || Rs2Inventory.contains(ItemID.TBWT_KARAMBWAN_VESSEL_LOADED_WITH_KARAMBWANJI);
        boolean hasBait = Rs2Inventory.contains(ItemID.TBWT_RAW_KARAMBWANJI);
        boolean hasBlessing = Rs2Equipment.isWearing("Rada's blessing");

        if ((hasStaffEquipped || hasStaffInInv) && hasVessel && hasBait) {
            if (!hasStaffEquipped) {
                if (Rs2Inventory.contains(ItemID.DRAMEN_STAFF)) {
                    Rs2Inventory.interact(ItemID.DRAMEN_STAFF, "Wield");
                    sleepUntil(() -> Rs2Equipment.isWearing(ItemID.DRAMEN_STAFF));
                } else if (Rs2Inventory.contains(ItemID.LUNAR_MOONCLAN_LIMINAL_STAFF)) {
                    Rs2Inventory.interact(ItemID.LUNAR_MOONCLAN_LIMINAL_STAFF, "Wield");
                    sleepUntil(() -> Rs2Equipment.isWearing(ItemID.LUNAR_MOONCLAN_LIMINAL_STAFF));
                }
            }
            botStatus = states.WALKING_TO_FISH;
            return;
        }

        walkToBank();
        Rs2Bank.openBank();
        sleepUntil(Rs2Bank::isOpen);

        Rs2Bank.depositAllExcept(ItemID.FISH_BARREL_OPEN, ItemID.FISH_BARREL_CLOSED);

        if (!Rs2Inventory.hasItem(ItemID.FISH_BARREL_OPEN) && !Rs2Inventory.hasItem(ItemID.FISH_BARREL_CLOSED)) {
            if (Rs2Bank.hasItem(ItemID.FISH_BARREL_OPEN)) {
                Rs2Bank.withdrawItem(ItemID.FISH_BARREL_OPEN);
            } else if (Rs2Bank.hasItem(ItemID.FISH_BARREL_CLOSED)) {
                Rs2Bank.withdrawItem(ItemID.FISH_BARREL_CLOSED);
            }
        }

        if (!hasStaffEquipped && !hasStaffInInv) {
            if (Rs2Bank.hasItem(ItemID.DRAMEN_STAFF)) {
                Rs2Bank.withdrawAndEquip(ItemID.DRAMEN_STAFF);
            } else if (Rs2Bank.hasItem(ItemID.LUNAR_MOONCLAN_LIMINAL_STAFF)) {
                Rs2Bank.withdrawAndEquip(ItemID.LUNAR_MOONCLAN_LIMINAL_STAFF);
            }
        } else if (!hasStaffEquipped) {
            if (Rs2Inventory.contains(ItemID.DRAMEN_STAFF)) {
                Rs2Inventory.interact(ItemID.DRAMEN_STAFF, "Wield");
                sleepUntil(() -> Rs2Equipment.isWearing(ItemID.DRAMEN_STAFF));
            } else if (Rs2Inventory.contains(ItemID.LUNAR_MOONCLAN_LIMINAL_STAFF)) {
                Rs2Inventory.interact(ItemID.LUNAR_MOONCLAN_LIMINAL_STAFF, "Wield");
                sleepUntil(() -> Rs2Equipment.isWearing(ItemID.LUNAR_MOONCLAN_LIMINAL_STAFF));
            }
        }

        if (!hasBlessing && Rs2Bank.hasItem("Rada's blessing")) {
            Rs2Bank.withdrawAndEquip("Rada's blessing");
        }

        if (!hasVessel) {
            if (Rs2Bank.hasItem(ItemID.TBWT_KARAMBWAN_VESSEL_LOADED_WITH_KARAMBWANJI)) {
                Rs2Bank.withdrawItem(ItemID.TBWT_KARAMBWAN_VESSEL_LOADED_WITH_KARAMBWANJI);
            } else if (Rs2Bank.hasItem(ItemID.TBWT_KARAMBWAN_VESSEL)) {
                Rs2Bank.withdrawItem(ItemID.TBWT_KARAMBWAN_VESSEL);
            }
        }

        if (!hasBait && Rs2Bank.hasItem(ItemID.TBWT_RAW_KARAMBWANJI)) {
            Rs2Bank.withdrawAll(ItemID.TBWT_RAW_KARAMBWANJI);
        }

        Rs2Bank.closeBank();

        if (Rs2Inventory.hasItem(ItemID.FISH_BARREL_CLOSED)) {
            Rs2Inventory.interact(ItemID.FISH_BARREL_CLOSED,"Open");
        }
        botStatus = states.WALKING_TO_FISH;
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
        WorldPoint nearestBank = Rs2Bank.getNearestBank().getWorldPoint();
        Rs2Walker.walkTo(nearestBank, 20);
    }

    private void useBank() {
        deposit_inv();
    }

    private void deposit_inv() {
        Rs2Bank.openBank();
        sleepUntil(Rs2Bank::isOpen);

        Rs2Bank.depositAllExcept(
                ItemID.FISH_BARREL_OPEN,
                ItemID.FISH_BARREL_CLOSED,
                ItemID.TBWT_KARAMBWAN_VESSEL,
                ItemID.TBWT_KARAMBWAN_VESSEL_LOADED_WITH_KARAMBWANJI,
                ItemID.TBWT_RAW_KARAMBWANJI

        );

        if (Rs2Inventory.hasItem(ItemID.FISH_BARREL_OPEN) || Rs2Inventory.hasItem(ItemID.FISH_BARREL_CLOSED)) {
            Rs2Bank.emptyFishBarrel();
        }
    }

    private void interactWithFishingSpot() {
        Rs2Npc.interact(NpcID._0_45_48_KARAMBWAN, "Fish");
    }

    private void setupBaitFishing() {
        walkToBank();
        deposit_inv();
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
        Rs2Walker.walkTo(fishingPoint, 15);
        interactWithFishingSpot();
    }
}