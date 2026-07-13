package net.runelite.client.plugins.microbot.netokarambwans;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.NpcID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2RunePouch;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.magic.Runes;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import javax.inject.Inject;
import net.runelite.client.plugins.microbot.shared.session.NetoBreakManager;
import net.runelite.client.plugins.microbot.shared.session.NetoWorldHopManager;
import net.runelite.client.plugins.microbot.shared.session.NetoRuntimeDisable;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static net.runelite.client.plugins.microbot.netokarambwans.KarambwanInfo.botStatus;
import static net.runelite.client.plugins.microbot.netokarambwans.KarambwanInfo.states;

@Slf4j
public class KarambwansScript extends Script {
    public static double version = 1.9;

    @Inject
    private NetoKaramPlugin plugin;
    @Inject
    private NetoBreakManager breakManager;
    @Inject
    private NetoWorldHopManager worldHopManager;
    @Inject
    private NetoRuntimeDisable runtimeDisable;
    private static final int CAMERA_PITCH = 512;
    private static final int CAMERA_YAW = 0;
    private static final int CAMERA_ZOOM = 230;
    private static final int[] CONSTRUCTION_CAPE_IDS = {9789, 9790};
    private static final int[] CRAFTING_CAPE_IDS = {9780, 9781};
    private static final Map<Runes, Integer> RUNE_POUCH_RUNES = Map.of(
            Runes.AIR, 16000,
            Runes.EARTH, 16000,
            Runes.LAW, 16000
    );
    private final WorldPoint fishingPoint = new WorldPoint(2899, 3118, 0);
    private final WorldPoint baitPoint = new WorldPoint(2804, 3006, 0);
    private boolean cameraSet = false;


    public boolean run(KarambwansConfig config) {
        cameraSet = false;
        Microbot.enableAutoRunOn = true;

        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyRunecraftingSetup();
        Rs2Antiban.setActivity(Activity.GENERAL_FISHING);

        breakManager.configure(config, "Neto Karambwans");
        worldHopManager.configure(config, "Neto Karambwans");
        runtimeDisable.configure(config, "Neto Karambwans");

        breakManager.reset();
        worldHopManager.reset();
        runtimeDisable.reset();

        Rs2Antiban.setActivity(Activity.CATCHING_RAW_KARAMBWAN);
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) {
                    if (runtimeDisable.updateRuntime(NetoKaramPlugin.class)) return;
                    if (breakManager.updateBreakState()) return;
                    return;
                }
                if (!super.run()) return;

                if (!cameraSet) {
                    setCameraPosition(CAMERA_PITCH, CAMERA_YAW, CAMERA_ZOOM);
                    cameraSet = true;
                }

                log.info("[KarambwansDebug] Main Loop - Current botStatus: {}", botStatus);

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
                        if (Rs2Bank.isOpen()) {
                            Rs2Bank.closeBank();
                            sleepUntil(() -> !Rs2Bank.isOpen(), 3000);
                        }
                        if (checkForBreakOrHop()) {
                            return;
                        }
                        botStatus = states.WALKING_TO_FISH;
                        break;
                    case WALKING_TO_FISH:
                        if (walkToFish()) {
                            botStatus = states.FISHING;
                        }
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

    private void setCameraPosition(int pitch, int yaw, int zoom) {
        Microbot.getClientThread().invokeLater(() -> {
            Microbot.getClient().setCameraPitchTarget(pitch);
            Microbot.getClient().setCameraYawTarget(yaw);
        });
        Rs2Camera.setZoom(zoom);
    }

    @Override
    public void shutdown() {
        breakManager.reset();
        worldHopManager.reset();
        runtimeDisable.reset();
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

        boolean hasFairyCons = Microbot.getClient().getRealSkillLevel(Skill.CONSTRUCTION) >= 84;
        if (hasFairyCons) {
            boolean has99Construction = Microbot.getClient().getRealSkillLevel(Skill.CONSTRUCTION) >= 99;
            if (has99Construction) {
                equipConstructionCape();
            } else {
                ensureRunePouchLoaded();
            }

            handleCraftingCape(has99Construction);
        }

        Rs2Bank.closeBank();

        if (Rs2Inventory.hasItem(ItemID.FISH_BARREL_CLOSED)) {
            Rs2Inventory.interact(ItemID.FISH_BARREL_CLOSED,"Open");
        }
        botStatus = states.WALKING_TO_FISH;
    }

    private void fishingLoop() {
        log.info("[KarambwansDebug] fishingLoop: isFull={}, count={}, capacity={}, emptySlots={}, isAnimating={}, isInteracting={}",
                 Rs2Inventory.isFull(), Rs2Inventory.count(), Rs2Inventory.capacity(), Rs2Inventory.emptySlotCount(),
                 Rs2Player.isAnimating(), Rs2Player.isInteracting());
        log.info("[KarambwansDebug] Inventory items: {}", Rs2Inventory.all().stream()
                 .map(item -> item.getName() + " (id=" + item.getId() + ", slot=" + item.getSlot() + ")")
                 .collect(Collectors.joining(", ")));
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
            sleepUntil(() -> Rs2Player.isAnimating() || Rs2Inventory.isFull(), 3000);
        }
    }

    private void walkToBank() {
        BankLocation nearestBankLoc = Rs2Bank.getNearestBank();
        WorldPoint nearestBank = null;
        if (nearestBankLoc != null) {
            nearestBank = nearestBankLoc.getWorldPoint();
        }
        if (nearestBank == null) {
            log.warn("[KarambwansDebug] Rs2Bank.getNearestBank() returned null! Attempting fallback teleport...");
            if (teleportCraftingCape()) {
                sleepUntil(() -> Rs2Bank.getNearestBank() != null, 5000);
                nearestBankLoc = Rs2Bank.getNearestBank();
                if (nearestBankLoc != null) {
                    nearestBank = nearestBankLoc.getWorldPoint();
                }
            } else if (teleportConstructionCape()) {
                sleepUntil(() -> Rs2Bank.getNearestBank() != null, 5000);
                nearestBankLoc = Rs2Bank.getNearestBank();
                if (nearestBankLoc != null) {
                    nearestBank = nearestBankLoc.getWorldPoint();
                }
            }
        }
        if (nearestBank != null) {
            walkTo(nearestBank, 10);
        } else {
            log.error("[KarambwansDebug] walkToBank: Could not find any bank location!");
        }
    }

    private boolean teleportCraftingCape() {
        for (int id : CRAFTING_CAPE_IDS) {
            if (Rs2Equipment.isWearing(id)) {
                log.info("[KarambwansDebug] Teleporting using worn Crafting Cape");
                return Rs2Equipment.interact(id, "Teleport");
            }
            if (Rs2Inventory.contains(id)) {
                log.info("[KarambwansDebug] Teleporting using Crafting Cape in inventory");
                return Rs2Inventory.interact(id, "Teleport");
            }
        }
        return false;
    }

    private boolean teleportConstructionCape() {
        for (int id : CONSTRUCTION_CAPE_IDS) {
            if (Rs2Equipment.isWearing(id)) {
                log.info("[KarambwansDebug] Teleporting using worn Construction Cape");
                return Rs2Equipment.interact(id, "Tele to POH");
            }
            if (Rs2Inventory.contains(id)) {
                log.info("[KarambwansDebug] Teleporting using Construction Cape in inventory");
                return Rs2Inventory.interact(id, "Tele to POH");
            }
        }
        return false;
    }

    private boolean walkTo(WorldPoint dst, int distance) {
        WorldPoint myLocation = Rs2Player.getWorldLocation();
        if (myLocation == null) {
            return false;
        }
        if (myLocation.distanceTo(dst) <= distance) {
            return true;
        }
        var future = scheduledExecutorService.submit(() -> Rs2Walker.walkTo(dst));

        while (!future.isDone()) {
            WorldPoint currentLocation = Rs2Player.getWorldLocation();
            if (currentLocation != null && currentLocation.distanceTo(dst) <= distance) {
                Rs2Walker.setTarget(null);
                future.cancel(true);
                return true;
            }
            sleep(100);
        }
        WorldPoint currentLocation = Rs2Player.getWorldLocation();
        return currentLocation != null && currentLocation.distanceTo(dst) <= distance;
    }

    private void useBank() {
        deposit_inv();
    }

    private void deposit_inv() {

        Rs2Tab.switchTo(InterfaceTab.INVENTORY); // IMPORTANT FOR INV INTERACTIONS
        Rs2Bank.openBank();
        sleepUntil( ()-> Rs2Bank.isOpen());

        Rs2Bank.emptyFishBarrel();

        Rs2Bank.depositAllExcept(
                ItemID.FISH_BARREL_OPEN,
                ItemID.FISH_BARREL_CLOSED,
                ItemID.TBWT_KARAMBWAN_VESSEL,
                ItemID.TBWT_KARAMBWAN_VESSEL_LOADED_WITH_KARAMBWANJI,
                ItemID.TBWT_RAW_KARAMBWANJI,
                ItemID.BH_RUNE_POUCH,
                ItemID.SKILLCAPE_CONSTRUCTION,
                ItemID.SKILLCAPE_CONSTRUCTION_TRIMMED,
                ItemID.SKILLCAPE_CRAFTING,
                ItemID.SKILLCAPE_CRAFTING_TRIMMED
        );
    }

    private boolean interactWithFishingSpot() {
        return Rs2Npc.interact(NpcID._0_45_48_KARAMBWAN, "Fish");
    }

    private void setupBaitFishing() {
        walkToBank();
        deposit_inv();
        Rs2Bank.withdrawItem(ItemID.NET);
        if (Rs2Bank.isOpen()) {
            Rs2Bank.closeBank(); sleepGaussian(600,200);
        }
        walkTo(baitPoint, 5);
    }

    private void baitingLoop(KarambwansConfig config) {
        if (Rs2Inventory.itemQuantity(ItemID.TBWT_RAW_KARAMBWANJI) >= config.karambwanjiToFish()) {
            Rs2Inventory.dropAll("Raw shrimps");
            if (checkForBreakOrHop()) {
                return;
            }
            botStatus = states.WALKING_TO_FISH;
            return;
        }

        if (Rs2Inventory.isFull()) {
            Rs2Inventory.dropAll("Raw shrimps");
        }

        if (!Rs2Player.isInteracting() && !Rs2Player.isAnimating()) {
            Rs2Npc.interact("Fishing spot", "Net"); // Generic name interaction is fine here
            sleepUntil(() -> Rs2Player.isAnimating() || Rs2Inventory.isFull(), 3000);
            sleepGaussian(1500,1000);
        }
    }

    private boolean walkToFish() {
        if (Rs2Bank.isOpen()) {
            Rs2Bank.closeBank(); sleepGaussian(600,200);
        }
        boolean reachedFishingArea = walkTo(fishingPoint, 10);
        if (!reachedFishingArea) {
            log.warn("[KarambwansDebug] Failed to reach fishing area; retrying walking state.");
            return false;
        }
        boolean fishingInteractionStarted = interactWithFishingSpot();
        boolean fishingStarted = sleepUntil(() -> Rs2Player.isAnimating() || Rs2Inventory.isFull(), 3000);
        return shouldAdvanceToFishing(reachedFishingArea, fishingInteractionStarted || fishingStarted);
    }

    static boolean shouldAdvanceToFishing(boolean reachedFishingArea, boolean fishingStarted) {
        return reachedFishingArea || fishingStarted;
    }

    private void equipConstructionCape() {
        if (!ensureCapeAvailable(CONSTRUCTION_CAPE_IDS)) {
            return;
        }

        if (!isWearingAny(CONSTRUCTION_CAPE_IDS)) {
            equipCape(CONSTRUCTION_CAPE_IDS);
        }
    }

    private void ensureRunePouchLoaded() {
        if (!Rs2Inventory.hasRunePouch()) {
            if (Rs2Bank.hasItem(ItemID.BH_RUNE_POUCH) && Rs2Bank.withdrawItem(ItemID.BH_RUNE_POUCH)) {
                sleepUntil(Rs2Inventory::hasRunePouch);
            }
        }

        if (Rs2Inventory.hasRunePouch()) {
            Rs2RunePouch.load(RUNE_POUCH_RUNES);
        }
    }

    private void handleCraftingCape(boolean has99Construction) {
        if (!ensureCapeAvailable(CRAFTING_CAPE_IDS)) {
            return;
        }

        if (!has99Construction && !isWearingAny(CRAFTING_CAPE_IDS)) {
            equipCape(CRAFTING_CAPE_IDS);
        }
    }

    private boolean ensureCapeAvailable(int[] capeIds) {
        if (isWearingAny(capeIds) || inventoryContainsAny(capeIds)) {
            return true;
        }

        for (int capeId : capeIds) {
            if (Rs2Bank.hasItem(capeId) && Rs2Bank.withdrawItem(capeId)) {
                int finalCapeId = capeId;
                sleepUntil(() -> Rs2Inventory.contains(finalCapeId));
                if (Rs2Inventory.contains(finalCapeId)) {
                    return true;
                }
            }
        }

        return isWearingAny(capeIds) || inventoryContainsAny(capeIds);
    }

    private void equipCape(int[] capeIds) {
        for (int capeId : capeIds) {
            if (Rs2Inventory.contains(capeId)) {
                Rs2Inventory.interact(capeId, "Wear");
                int finalCapeId = capeId;
                sleepUntil(() -> Rs2Equipment.isWearing(finalCapeId) || !Rs2Inventory.contains(finalCapeId));
                if (Rs2Equipment.isWearing(finalCapeId)) {
                    break;
                }
            }
        }
    }

    private boolean isWearingAny(int... itemIds) {
        for (int itemId : itemIds) {
            if (Rs2Equipment.isWearing(itemId)) {
                return true;
            }
        }
        return false;
    }

    private boolean inventoryContainsAny(int... itemIds) {
        for (int itemId : itemIds) {
            if (Rs2Inventory.contains(itemId)) {
                return true;
            }
        }
        return false;
    }

    private boolean checkForBreakOrHop() {
        if (runtimeDisable.updateRuntime(NetoKaramPlugin.class)) {
            return true;
        }

        if (breakManager.tryStartBreakAtSafePoint()) {
            return true;
        }

        if (worldHopManager.tryHopIfDue(this::isRunning).isAttempted()) {
            return true;
        }

        return false;
    }
}
