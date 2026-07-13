package net.runelite.client.plugins.microbot.netomahoganyhomes;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.shortestpath.ShortestPathPlugin;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.coords.Rs2WorldPoint;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spells;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.walker.WalkerState;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2RunePouch;
import net.runelite.client.plugins.microbot.util.magic.Runes;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.*;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
public class NetoMahoganyHomesScript extends Script {

    private static final int HOSIDIUS_UP_LADDER_ID = 11794;
    private static final int HOSIDIUS_DOWN_LADDER_ID = 11802;
    private static final int REPAIR_OBJECT_REACHED_DISTANCE = 3;
    private static final int[] DUELING_RING_IDS_LOWEST_CHARGE_FIRST = {
            ItemID.RING_OF_DUELING1,
            ItemID.RING_OF_DUELING2,
            ItemID.RING_OF_DUELING3,
            ItemID.RING_OF_DUELING4,
            ItemID.RING_OF_DUELING5,
            ItemID.RING_OF_DUELING6,
            ItemID.RING_OF_DUELING7,
            ItemID.RING_OF_DUELING8
    };
    private static final int STEEL_BAR_WITHDRAWAL_AMOUNT = 4;

    @Inject
    NetoMahoganyHomesPlugin plugin;

    private enum PrepState {
        NOT_STARTED,
        WALKING_TO_BANK,
        OPENING_BANK,
        DEPOSITING_EQUIPMENT,
        DEPOSITING_INVENTORY,
        EQUIPPING_OUTFIT,
        DEPOSITING_INVENTORY_AGAIN,
        WITHDRAWING_TOOLS,
        WITHDRAWING_SUPPLIES,
        FINISHED
    }

    private PrepState prepState = PrepState.NOT_STARTED;

    public boolean run(NetoMahoganyHomesConfig config) {
        prepState = PrepState.NOT_STARTED;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;

                if (prepState == PrepState.NOT_STARTED) {
                    Rs2Antiban.setActivityIntensity(ActivityIntensity.LOW);
                    Rs2Camera.setZoom(238);
                    Rs2Camera.setPitch(2800); // 2800 client pitch / 8
                }

                if (prepState != PrepState.FINISHED) {
                    executePrep();
                    return;
                }

                fix();
                finish();
                getNewContract();
                bank();
                walkToHome();


            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 50, TimeUnit.MILLISECONDS);
        return true;
    }

    private List<GameObject> getFixableObjects() {
        List<GameObject> objects = plugin.getObjectsToMark();
        List<Hotspot> fixableHotspots = Hotspot.getBrokenHotspots();
        HotspotObjects hotspotObjects = plugin.getCurrentHome().getHotspotObjects();

        // Precompute the set of IDs
        Set<Integer> ids = fixableHotspots.stream()
                .map(hotspot -> hotspotObjects.objects[hotspot.ordinal()].getObjectId())
                .collect(Collectors.toSet());

        // Filter using the precomputed set
        return objects.stream()
                .filter(Objects::nonNull)
                .filter(o -> ids.contains(o.getId()))
                .collect(Collectors.toList());
    }

    // Custom logging methods
    private void log(String message) {
        if (plugin.getConfig().logMessages()) {
            Microbot.log(message);
        }
    }

    private void log(String format, Object... args) {
        if (plugin.getConfig().logMessages()) {
            Microbot.log(String.format(format, args));
        }
    }

    private void logInfo(String message) {
        if (plugin.getConfig().logMessages()) {
            log.info(message);
        }
    }

    private void logInfo(String format, Object... args) {
        if (plugin.getConfig().logMessages()) {
            log.info(format, args);
        }
    }

    // Tasks section

    private int planksInPlankSack() {
        if (!plugin.getConfig().usePlankSack() || !Rs2Inventory.contains(ItemID.PLANK_SACK)) {
            return 0;
        }
        return Microbot.getVarbitValue(VarbitID.PLANK_SACK_PLAIN)
                + Microbot.getVarbitValue(VarbitID.PLANK_SACK_OAK)
                + Microbot.getVarbitValue(VarbitID.PLANK_SACK_TEAK)
                + Microbot.getVarbitValue(VarbitID.PLANK_SACK_MAHOGANY)
                + Microbot.getVarbitValue(VarbitID.PLANK_SACK_CAMPHOR)
                + Microbot.getVarbitValue(VarbitID.PLANK_SACK_IRONWOOD)
                + Microbot.getVarbitValue(VarbitID.PLANK_SACK_ROSEWOOD);
    }

    private void fix() {
        if (plugin.getCurrentHome() == null
                || !plugin.getCurrentHome().isInside(Rs2Player.getWorldLocation())
                || Hotspot.isEverythingFixed()) {
            return;
        }

        if (Rs2Widget.isWidgetVisible(InterfaceID.PohFurnitureCreation.FRAME)){
            Microbot.log("Out of plank and furniture creation widget pop up");
            bank();
            return;
        }

        Rs2WorldPoint playerLocation = Rs2Player.getRs2WorldPoint();
        NetoMahoganyHomesOverlay.setFixableObjects(getFixableObjects());

        // Sort fixable objects by plane and distance
        List<GameObject> sortedObjects = getFixableObjects().stream()
                .sorted(Comparator.comparingInt(TileObject::getPlane).thenComparingInt(o -> o.getWorldLocation().distanceTo2D(playerLocation.getWorldPoint())))
                .collect(Collectors.toList());


        GameObject object = sortedObjects.stream()
                .findFirst()
                .orElse(null);

        if (object == null) {
            log("No fixable objects found.");
            return;
        }

        if (handleFloorTransition(object.getWorldLocation().getPlane())) {
            return;
        }


        // Find the closest walkable tile around the object
        Rs2WorldPoint objectLocation = Rs2Tile.getNearestWalkableTile(object);


        int pathDistance = objectLocation != null ? objectLocation.distanceToPath(playerLocation.getWorldPoint()) : Integer.MAX_VALUE;
        log("Local Path Distance: " + pathDistance);

        if (pathDistance > 20) {
            if (openDoorToObject(object, objectLocation)) {
                return;
            }
            log("Local Path Distance is too far or unreachable, switching to WebWalker.");

            WalkerState state = walkToRepairObject(object.getWorldLocation());
            if (state == WalkerState.UNREACHABLE) {
                if (Rs2Player.getWorldLocation().getPlane() != object.getWorldLocation().getPlane()) {
                    tryToUseLadder();
                } else {
                    log("All pathing failed, trying to interact anyways.");
                    interactWithObject(object);
                }
            } else if (state == WalkerState.ARRIVED) {
                log("Arrived at object, trying to interact.");
                interactWithObject(object);
            }

        } else
            interactWithObject(object);

    }

    private WalkerState walkToRepairObject(WorldPoint destination) {
        var future = scheduledExecutorService.submit(() -> Rs2Walker.walkTo(destination));

        while (!future.isDone()) {
            WorldPoint playerLocation = Rs2Player.getWorldLocation();
            if (playerLocation != null
                    && playerLocation.getPlane() == destination.getPlane()
                    && playerLocation.distanceTo2D(destination) <= REPAIR_OBJECT_REACHED_DISTANCE) {
                Rs2Walker.setTarget(null, "netomahoganyhomes:repair-object-within-three-tiles");
                future.cancel(true);
                return WalkerState.ARRIVED;
            }
            sleep(100);
        }

        try {
            return Boolean.TRUE.equals(future.get()) ? WalkerState.ARRIVED : WalkerState.UNREACHABLE;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return WalkerState.EXIT;
        } catch (ExecutionException e) {
            log("Repair-object walking failed: %s", e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
            return WalkerState.UNREACHABLE;
        }
    }

    private void interactWithObject(GameObject object) {
        Hotspot hotspot = Hotspot.getByObjectId(object.getId());
        String action = Objects.requireNonNull(hotspot).getRequiredAction();
        if (Microbot.getRs2TileObjectCache().query().withId(object.getId()).interact(action)) {
            sleepUntil(() -> {
                String newAction = Objects.requireNonNull(Hotspot.getByObjectId(object.getId())).getRequiredAction();
                return !newAction.equals(action);
            }, 5000);
            sleep(Rs2Random.randomGaussian(300, 50));
        }

    }

    private boolean openDoorToObject(GameObject object, Rs2WorldPoint objectLocation) {
        if (Rs2Player.getWorldLocation().getPlane() != object.getWorldLocation().getPlane()) {
            return false;
        }
        log("Local Path seems to be blocked, checking for doors to open.");
        List<WorldPoint> walkerPath = Rs2Walker.getWalkPath(objectLocation.getWorldPoint());
        List<TileObject> doors = new ArrayList<>();
        for (WorldPoint wp : walkerPath) {
            TileObject door = null;
            var tile = Rs2Walker.getTile(wp);

            if (tile != null)
                door = tile.getWallObject();

            if (door == null) continue;

            var doorModel = Microbot.getRs2TileObjectCache().query().withId(door.getId()).nearest();
            if (doorModel == null) continue;
            var objectComp = doorModel.getObjectComposition();
            if (objectComp == null) continue;

            String name = objectComp.getName();

            if (Arrays.asList(objectComp.getActions()).contains("Open") && !name.equalsIgnoreCase("Chest")) {
                doors.add(door);
            }

        }

        List<String> doorNames = doors.stream()
                .map(d -> {
                    var m = Microbot.getRs2TileObjectCache().query().withId(d.getId()).nearest();
                    return m != null ? m.getObjectComposition().getName() : "unknown";
                })
                .collect(Collectors.toList());

        System.out.println("Doors found: " + doorNames + " Size: " + doors.size());

//        logInfo("Found {} doors", doors.size());
//        log("Doors found: %s", doors.size());

        for (TileObject door : doors) {
            var doorObj = Microbot.getRs2TileObjectCache().query().withId(door.getId()).nearest();
            ObjectComposition doorComp = doorObj != null ? doorObj.getObjectComposition() : null;
            List<String> actions = null;
            if (doorComp != null) {
                actions = Arrays.asList(doorComp.getActions());
            }
            if (actions != null && actions.contains("Open")) {

                log("Opening door at: %s", door.getWorldLocation());
                logInfo("Opening door at: {}", door.getWorldLocation());
                if (Microbot.getRs2TileObjectCache().query().withId(door.getId()).interact("Open")) {
                    Rs2Player.waitForWalking();
                    sleep(200, 500);
                    // if it's the last door in the list return true
                    if (door.equals(doors.get(doors.size() - 1)))
                        return true;
                }
            }
        }
        return false;
    }

    private void tryToUseLadder() {
        log("Walker missing transport, trying to find ladder manually.");
        int plane = Rs2Player.getWorldLocation().getPlane();
        var closestLadder = Microbot.getRs2TileObjectCache().query().withIds(Arrays.stream(plugin.getCurrentHome().getLadders()).mapToInt(Integer::intValue).toArray()).nearest();
        if (closestLadder != null && closestLadder.click()) {
            sleepUntil(() -> Rs2Player.getWorldLocation().getPlane() != plane, 5000);
            sleep(Rs2Random.randomGaussian(300, 50));
        }
    }

    private void openMariahDoorIfClosed() {
        var door = Microbot.getRs2TileObjectCache().query()
                .withId(7452)
                .nearest();
        if (door != null && door.getWorldLocation().distanceTo(Rs2Player.getWorldLocation()) <= 5) {
            log("Opening Mariah second floor door...");
            if (door.click("Open")) {
                sleepUntil(() -> Microbot.getRs2TileObjectCache().query().withId(7452).nearest() == null, 3000);
                sleep(200, 600);
            }
        }
    }

    private int getUpLadderId(Home home) {
        switch (home) {
            case MARIAH:
            case LEELA:
                return 11794;
            case NORMAN:
                return 24082;
            case LARRY:
                return 24075;
            case JEFF:
                return 11789;
            case BOB:
                return 11797;
            case ROSS:
                return 16683;
            default:
                return -1;
        }
    }

    private int getDownLadderId(Home home) {
        switch (home) {
            case MARIAH:
            case LEELA:
                return 11802;
            case NORMAN:
                return 24085;
            case LARRY:
                return 24076;
            case JEFF:
                return 11793;
            case BOB:
                return 11799;
            case ROSS:
                return 16679;
            default:
                return -1;
        }
    }

    private boolean handleFloorTransition(int targetPlane) {
        Home currentHome = plugin.getCurrentHome();
        if (currentHome == null) {
            return false;
        }

        int currentPlane = Rs2Player.getWorldLocation().getPlane();
        if (currentPlane == targetPlane) {
            return false;
        }

        int ladderId;
        int expectedPlane;
        if (currentPlane == 0 && targetPlane == 1) {
            ladderId = getUpLadderId(currentHome);
            expectedPlane = 1;
        } else if (currentPlane == 1 && targetPlane == 0) {
            ladderId = getDownLadderId(currentHome);
            expectedPlane = 0;
        } else {
            log("Unsupported %s floor transition from plane %d to plane %d.", currentHome.getName(), currentPlane, targetPlane);
            return true;
        }

        if (ladderId == -1) {
            return false;
        }

        // Open Mariah's door before going down the ladder
        if (currentHome == Home.MARIAH && currentPlane == 1 && expectedPlane == 0) {
            openMariahDoorIfClosed();
        }

        var ladder = Microbot.getRs2TileObjectCache().query()
                .withId(ladderId)
                .where(obj -> obj.getWorldLocation().getPlane() == currentPlane)
                .nearest();
        if (ladder == null) {
            log("%s ladder %d was not found on plane %d; retrying.", currentHome.getName(), ladderId, currentPlane);
            return true;
        }

        log("Using %s ladder %d to move from plane %d to plane %d.", currentHome.getName(), ladderId, currentPlane, expectedPlane);
        if (!ladder.click()) {
            log("Failed to interact with %s ladder %d; retrying.", currentHome.getName(), ladderId);
            return true;
        }

        if (!sleepUntil(() -> Rs2Player.getWorldLocation().getPlane() == expectedPlane, 5000)) {
            log("%s ladder %d did not reach plane %d; retrying.", currentHome.getName(), ladderId, expectedPlane);
        }
        sleep(Rs2Random.randomGaussian(300, 50));

        // Open Mariah's door after climbing up the ladder to plane 1
        if (currentHome == Home.MARIAH && Rs2Player.getWorldLocation().getPlane() == 1) {
            openMariahDoorIfClosed();
        }

        return true;
    }


    // Finish by talking to the NPC
    private void finish() {
        if (plugin.getCurrentHome() != null
                && plugin.getCurrentHome().isInside(Rs2Player.getWorldLocation())
                && Hotspot.isEverythingFixed()) {
            if(plugin.getConfig().usePlankSack() && planksInPlankSack() > 0 && !Rs2Inventory.isFull()){
                if (Rs2Inventory.contains(ItemID.PLANK_SACK) && Rs2Inventory.contains(ItemID.STEEL_BAR)) {
                    Rs2ItemModel plankSack = Rs2Inventory.get(ItemID.PLANK_SACK);
                    if (plankSack != null) {
                        Rs2Inventory.interact(plankSack, "Empty");
                        sleep(Rs2Random.randomGaussian(800, 200));
                    }
                }
            }
            int npcPlane = (plugin.getCurrentHome() == Home.NORMAN || plugin.getCurrentHome() == Home.JESS) ? 1 : 0;
            if (handleFloorTransition(npcPlane)) {
                return;
            }
            var npc = Microbot.getRs2NpcCache().query().withId(plugin.getCurrentHome().getNpcId()).nearest();
            if (npc == null && Rs2Player.getWorldLocation().getPlane() != npcPlane) {
                log("We are on the wrong floor, Trying to find ladder to change floors");
                int playerPlane = Rs2Player.getWorldLocation().getPlane();

                var ladders = Microbot.getRs2TileObjectCache().query()
                        .withIds(Arrays.stream(plugin.getCurrentHome().getLadders()).mapToInt(Integer::intValue).toArray())
                        .where(obj -> obj.getWorldLocation().getPlane() == playerPlane)
                        .toList();
                var closestLadder2 = ladders.stream()
                        .min(Comparator.comparingInt(obj ->
                                obj.getWorldLocation().distanceTo(Rs2Player.getWorldLocation())))
                        .orElse(null);
                    if (closestLadder2 != null && closestLadder2.click()) {
                            sleepUntil(
                                    () -> Rs2Player.getWorldLocation().getPlane() == npcPlane
                                    , 5000);
                            return;
                    }
            }
            if (npc != null) {
                Rs2WorldPoint npcLocation = new Rs2WorldPoint(npc.getWorldLocation());
                log("Local NPC path distance: " + npcLocation.distanceToPath(Rs2Player.getWorldLocation()));
                if (npcLocation.distanceToPath(Rs2Player.getWorldLocation()) < 20) {
                    if (npc.click("Talk-to")) {
                        log("Getting reward from NPC");
                        sleepUntil(Rs2Dialogue::hasContinue, 10000);
                        if (Rs2Dialogue.hasDialogueText("Please excuse me, I'm rather busy.")) {
                            plugin.setCurrentHome(null);
                        }
                        sleepUntil(() -> !Rs2Dialogue.isInDialogue(), Rs2Dialogue::clickContinue, 6000, 300);
                        sleep(Rs2Random.randomGaussian(800, 100));

                        if (Rs2Player.getWorldLocation().getPlane() == 1 && Home.JESS.getArea().contains2D(Rs2Player.getWorldLocation())) {
                            log("Climbing down stairs at Jess's home...");
                            var stairs = Microbot.getRs2TileObjectCache().query().withId(16685).nearest();
                            if (stairs != null && stairs.click()) {
                                sleepUntil(() -> Rs2Player.getWorldLocation().getPlane() == 0, 5000);
                                sleep(Rs2Random.randomGaussian(600, 100));
                            }
                        }
                    }
                } else {
                    log("Local NPC path distance is too far, switching to WebWalker.");
                    Rs2Walker.walkTo(npc.getWorldLocation());
                    sleep(1200, 2200);
                }
            }
        }
    }

    // Get new contract
    private void getNewContract() {
        if (plugin.getCurrentHome() == null) {
            if(plugin.getConfig().useNpcContact()){
                if (Rs2Magic.npcContact("amy")) {
                    handleContractDialogue();
                }
                return;
            }
            WorldPoint contractLocation = getClosestContractLocation();
            int walkingDistance = new Rs2WorldPoint(Rs2Player.getWorldLocation()).distanceToPath(contractLocation);
            if (walkingDistance > 50) {
                if (Rs2Magic.canCast(Rs2Spells.ARDOUGNE_TELEPORT)) {
                    log("NPC is too far (%d tiles), teleporting to Ardougne...", walkingDistance);
                    Rs2Magic.cast(Rs2Spells.ARDOUGNE_TELEPORT);
                    sleepUntil(() -> !Rs2Player.isAnimating());
                    sleep(600, 1200);
                    contractLocation = ContractLocation.MAHOGANY_HOMES_ARDOUGNE.getLocation();
                } else {
                    log("NPC is too far (%d tiles), but cannot teleport to Ardougne. Walking anyway...", walkingDistance);
                }
            }
            if (contractLocation.distanceTo2D(Rs2Player.getWorldLocation()) > 10) {
                log("Walking to contract NPC");
                Rs2Walker.walkWithState(contractLocation, 5);

            } else {
                log("Getting new contract");


                // Search for Mahogany Homes contract NPCs directly by name
                var npc = Microbot.getRs2NpcCache().query().withNames("Amy", "Marlo", "Ellie", "Angelo").nearestOnClientThread();
                
                if (npc == null) {
                    log("No contract NPC found, waiting before retry");
                    sleep(2000, 3000);  // Wait 2-3 seconds to prevent spam
                    return;
                }
                log("NPC found: " + npc.getName());
                if (npc.click("Contract")) {
                    handleContractDialogue();
                }

            }

        }

    }

    public void handleContractDialogue() {
        // Reduced timeout and early return if dialogue not available
        if (!sleepUntil(Rs2Dialogue::hasSelectAnOption, Rs2Dialogue::clickContinue, 5000, 300)) {
            log("No dialogue options available, returning early");
            return;
        }
        Rs2Dialogue.keyPressForDialogueOption(plugin.getConfig().currentTier().getPlankSelection().getChatOption());
        sleepUntil(Rs2Dialogue::hasContinue, 5000);
        sleep(400, 800);
        sleepUntil(() -> !Rs2Dialogue.isInDialogue(), Rs2Dialogue::clickContinue, 6000, 300);
        sleep(Rs2Random.randomGaussian(800, 100));
    }

    // Bank if we need to
    private void bank() {
        Home currentHome = plugin.getCurrentHome();
        if (currentHome != null
                && plugin.distanceBetween(currentHome.getArea(), Rs2Player.getWorldLocation()) > 0
                && isMissingItems()) {
            BankLocation bankLocation = getResupplyBank(currentHome);
            if (bankLocation != null && Rs2Bank.walkToBank(bankLocation)) {
                if(Rs2Bank.openBank()) {
                    sleepUntil(Rs2Bank::isOpen);
                    withdrawSupplies();
                    if (Rs2Bank.isOpen()) {
                        Rs2Bank.closeBank();
                    }
                }

            }
        }
    }

    private BankLocation getResupplyBank(Home currentHome) {
        if (isWearingDuelingRing()) {
            WorldPoint castleWars = BankLocation.CASTLE_WARS.getWorldPoint();
            if (castleWars.distanceTo2D(Rs2Player.getWorldLocation()) > 15) {
                log("Teleporting to Castle Wars to resupply...");
                if (!Rs2Equipment.interact(DUELING_RING_IDS_LOWEST_CHARGE_FIRST, "Castle Wars")) {
                    return null;
                }
                if (!sleepUntil(() -> castleWars.distanceTo2D(Rs2Player.getWorldLocation()) <= 15, 10000)) {
                    log("Castle Wars teleport did not complete.");
                    return null;
                }
            }
            return BankLocation.CASTLE_WARS;
        }

        log("No Ring of dueling equipped; using the nearest bank fallback.");
        ShortestPathPlugin.getPathfinderConfig().setIgnoreTeleportAndItems(true);
        BankLocation bankLocation = Rs2Bank.getNearestBank(currentHome.getLocation());
        ShortestPathPlugin.getPathfinderConfig().setIgnoreTeleportAndItems(false);
        return bankLocation;
    }

    private boolean isWearingDuelingRing() {
        return Rs2Equipment.isWearing(DUELING_RING_IDS_LOWEST_CHARGE_FIRST);
    }

    private boolean ensureDuelingRingEquipped() {
        if (isWearingDuelingRing()) {
            return true;
        }

        for (int ringId : DUELING_RING_IDS_LOWEST_CHARGE_FIRST) {
            if (Rs2Bank.hasItem(ringId)) {
                log("Equipping the lowest-charge Ring of dueling available.");
                Rs2Bank.withdrawAndEquip(ringId);
                return sleepUntil(this::isWearingDuelingRing, 3000);
            }
        }

        log("No Ring of dueling found in the bank; nearest-bank fallback will be used.");
        return false;
    }

    private boolean withdrawSupplies() {
        ensureDuelingRingEquipped();

        int plankId = plugin.getConfig().currentTier().getPlankSelection().getPlankId();
        int availableSteelBars = Rs2Bank.count(ItemID.STEEL_BAR) + steelBarsInInventory();
        if (availableSteelBars < STEEL_BAR_WITHDRAWAL_AMOUNT) {
            log("Out of steel bars.");
            Microbot.showMessage("Not enough steel bars in the bank. Stopping plugin.");
            Microbot.stopPlugin(plugin);
            return false;
        }

        if (steelBarsInInventory() > STEEL_BAR_WITHDRAWAL_AMOUNT) {
            Rs2Bank.depositAll(ItemID.STEEL_BAR);
            Rs2Inventory.waitForInventoryChanges(3000);
        }
        if (steelBarsInInventory() < STEEL_BAR_WITHDRAWAL_AMOUNT) {
            Rs2Bank.withdrawX(ItemID.STEEL_BAR, STEEL_BAR_WITHDRAWAL_AMOUNT - steelBarsInInventory());
            Rs2Inventory.waitForInventoryChanges(3000);
        }

        int sackSpace = plugin.getConfig().usePlankSack() && Rs2Inventory.contains(ItemID.PLANK_SACK)
                ? Math.max(0, 28 - planksInPlankSack())
                : 0;
        int planksToWithdraw = sackSpace + Rs2Inventory.emptySlotCount();
        if (Rs2Bank.count(plankId) < planksToWithdraw) {
            log("Not enough planks to fill the plank sack and inventory.");
            Microbot.showMessage("Not enough selected planks in the bank. Stopping plugin.");
            Microbot.stopPlugin(plugin);
            return false;
        }

        if (plugin.getConfig().usePlankSack() && Rs2Inventory.contains(ItemID.PLANK_SACK)) {
            fillPlankSack(plankId);
        }

        if (!Rs2Bank.isOpen()) {
            Rs2Bank.openBank();
            sleepUntil(Rs2Bank::isOpen, 3000);
        }
        if (Rs2Inventory.emptySlotCount() > 0) {
            Rs2Bank.withdrawAll(plankId);
            Rs2Inventory.waitForInventoryChanges(3000);
        }
        return true;
    }

    private void fillPlankSack(int plankId) {
        for (int attempt = 0; attempt < 2 && planksInPlankSack() < 28; attempt++) {
            if (!Rs2Bank.isOpen()) {
                Rs2Bank.openBank();
                if (!sleepUntil(Rs2Bank::isOpen, 3000)) {
                    return;
                }
            }
            Rs2Bank.withdrawAll(plankId);
            Rs2Inventory.waitForInventoryChanges(1000);
            sleep(Rs2Random.randomGaussian(800, 200));
            Rs2ItemModel plankSack = Rs2Inventory.get(ItemID.PLANK_SACK);
            if (plankSack != null) {
                Rs2Inventory.interact(plankSack, "Fill");
                Rs2Inventory.waitForInventoryChanges(3000);
            }
        }
    }

    private boolean isVarrockHome(Home home) {
        return home == Home.BOB || home == Home.JEFF || home == Home.SARAH;
    }

    // Walk to current home
    private void walkToHome() {
        Home currentHome = plugin.getCurrentHome();
        if (currentHome != null
                && plugin.distanceBetween(currentHome.getArea(), Rs2Player.getWorldLocation()) > 0
                && !isMissingItems()) {

            if (isVarrockHome(currentHome) && Rs2Player.getWorldLocation().distanceTo(currentHome.getLocation()) > 100) {
                if (Rs2Magic.canCast(Rs2Spells.VARROCK_TELEPORT)) {
                    log("Teleporting to Varrock Square before walking...");
                    Rs2Magic.cast(Rs2Spells.VARROCK_TELEPORT);
                    sleepUntil(() -> !Rs2Player.isAnimating());
                    sleep(600, 1200);
                    return;
                }
            }

            Rs2Walker.walkWithState(plugin.getCurrentHome().getLocation(), 3);
        }
    }

    private boolean isMissingItems() {
        return (planksInInventory() + planksInPlankSack()) < planksNeeded()
                || steelBarsInInventory() < steelBarsNeeded();
    }

    private int planksNeeded() {
        return plugin.getCurrentHome().getRequiredPlanks(plugin.getContractTier());
    }

    private int steelBarsNeeded() {
        return plugin.getCurrentHome().getRequiredSteelBars(plugin.getContractTier());
    }

    private int planksInInventory() {
        return Rs2Inventory.count(plugin.getConfig().currentTier().getPlankSelection().getPlankId());
    }

    private int steelBarsInInventory() {
        return Rs2Inventory.count(ItemID.STEEL_BAR);
    }

    // Get closest contract location
    private WorldPoint getClosestContractLocation() {
        List<WorldPoint> contractLocations = new ArrayList<>();
        contractLocations.add(ContractLocation.MAHOGANY_HOMES_ARDOUGNE.getLocation());
        contractLocations.add(ContractLocation.MAHOGANY_HOMES_FALADOR.getLocation());
        contractLocations.add(ContractLocation.MAHOGANY_HOMES_HOSIDIUS.getLocation());
        contractLocations.add(ContractLocation.MAHOGANY_HOMES_VARROCK.getLocation());

        return contractLocations.stream()
                .min(Comparator.comparingInt(wp -> wp.distanceTo2D(Rs2Player.getWorldLocation())))
                .orElse(null);
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }

    private void executePrep() {
        if (prepState == PrepState.FINISHED) return;

        // For states that require the bank, ensure it's open (except when walking or opening)
        if (prepState != PrepState.NOT_STARTED && prepState != PrepState.WALKING_TO_BANK && prepState != PrepState.OPENING_BANK) {
            if (!Rs2Bank.isOpen()) {
                log("Bank closed during prep, attempting to reopen...");
                Rs2Bank.openBank();
                return;
            }
        }

        switch (prepState) {
            case NOT_STARTED:
                log("Starting Mahogany Homes Prep state...");
                if (Rs2Bank.isOpen()) {
                    prepState = PrepState.DEPOSITING_EQUIPMENT;
                } else {
                    log("Walking to the closest bank...");
                    Rs2Bank.walkToBank();
                    prepState = PrepState.WALKING_TO_BANK;
                }
                break;

            case WALKING_TO_BANK:
                if (Rs2Bank.isOpen()) {
                    prepState = PrepState.DEPOSITING_EQUIPMENT;
                } else {
                    Rs2Bank.openBank();
                    prepState = PrepState.OPENING_BANK;
                }
                break;

            case OPENING_BANK:
                if (Rs2Bank.isOpen()) {
                    prepState = PrepState.DEPOSITING_EQUIPMENT;
                } else {
                    Rs2Bank.openBank();
                }
                break;

            case DEPOSITING_EQUIPMENT:
                if (Rs2Equipment.isWearing()) {
                    log("Depositing equipped items...");
                    Rs2Bank.depositEquipment();
                } else {
                    log("Equipment is empty.");
                    prepState = PrepState.DEPOSITING_INVENTORY;
                }
                break;

            case DEPOSITING_INVENTORY:
                if (!Rs2Inventory.isEmpty()) {
                    log("Depositing inventory...");
                    Rs2Bank.depositAll();
                } else {
                    log("Inventory is empty.");
                    prepState = PrepState.EQUIPPING_OUTFIT;
                }
                break;

            case EQUIPPING_OUTFIT:
                log("Equipping outfits...");
                equipOutfitPrep();
                prepState = PrepState.DEPOSITING_INVENTORY_AGAIN;
                break;

            case DEPOSITING_INVENTORY_AGAIN:
                if (!Rs2Inventory.isEmpty()) {
                    log("Depositing leftovers from outfit equipping...");
                    Rs2Bank.depositAll();
                } else {
                    prepState = PrepState.WITHDRAWING_TOOLS;
                }
                break;

            case WITHDRAWING_TOOLS:
                log("Withdrawing saw and hammer...");
                withdrawToolsPrep();
                prepState = PrepState.WITHDRAWING_SUPPLIES;
                break;

            case WITHDRAWING_SUPPLIES:
                log("Withdrawing four steel bars and planks...");
                if (!withdrawSupplies()) {
                    return;
                }
                // Close bank after we are done
                Rs2Bank.closeBank();
                prepState = PrepState.FINISHED;
                log("Mahogany Homes Prep state finished successfully.");
                break;
        }
    }

    private void withdrawAndEquipItem(String name) {
        if (Rs2Equipment.isWearing(name)) return;
        
        if (Rs2Inventory.hasItem(name)) {
            Rs2Inventory.wield(name);
            sleepUntil(() -> Rs2Equipment.isWearing(name), 1800);
            return;
        }
        
        if (Rs2Bank.hasItem(name)) {
            Rs2Bank.withdrawAndEquip(name);
        }
    }

    private void equipOutfitPrep() {
        // Head Slot: Carpenter's helmet OR Graceful hood
        if (Rs2Bank.hasItem("Carpenter's helmet") || Rs2Inventory.hasItem("Carpenter's helmet")) {
            withdrawAndEquipItem("Carpenter's helmet");
        } else if (Rs2Bank.hasItem("Graceful hood") || Rs2Inventory.hasItem("Graceful hood")) {
            withdrawAndEquipItem("Graceful hood");
        }

        // Torso Slot: Carpenter's jacket OR Graceful top
        if (Rs2Bank.hasItem("Carpenter's jacket") || Rs2Inventory.hasItem("Carpenter's jacket")) {
            withdrawAndEquipItem("Carpenter's jacket");
        } else if (Rs2Bank.hasItem("Graceful top") || Rs2Inventory.hasItem("Graceful top")) {
            withdrawAndEquipItem("Graceful top");
        }

        // Legs Slot: Carpenter's trousers OR Graceful legs
        if (Rs2Bank.hasItem("Carpenter's trousers") || Rs2Inventory.hasItem("Carpenter's trousers")) {
            withdrawAndEquipItem("Carpenter's trousers");
        } else if (Rs2Bank.hasItem("Graceful legs") || Rs2Inventory.hasItem("Graceful legs")) {
            withdrawAndEquipItem("Graceful legs");
        }

        // Boots Slot: Carpenter's boots OR Graceful boots
        if (Rs2Bank.hasItem("Carpenter's boots") || Rs2Inventory.hasItem("Carpenter's boots")) {
            withdrawAndEquipItem("Carpenter's boots");
        } else if (Rs2Bank.hasItem("Graceful boots") || Rs2Inventory.hasItem("Graceful boots")) {
            withdrawAndEquipItem("Graceful boots");
        }

        // Gloves Slot: Graceful gloves (always)
        if (Rs2Bank.hasItem("Graceful gloves") || Rs2Inventory.hasItem("Graceful gloves")) {
            withdrawAndEquipItem("Graceful gloves");
        }

        // Cape Slot: Graceful cape (always)
        if (Rs2Bank.hasItem("Graceful cape") || Rs2Inventory.hasItem("Graceful cape")) {
            withdrawAndEquipItem("Graceful cape");
        }

        // Ring Slot: use the lowest-charge Ring of dueling available
        ensureDuelingRingEquipped();
    }

    private void withdrawToolsPrep() {
        // Amy's saw OR Saw
        if (Rs2Equipment.isWearing("Amy's saw")) {
            // Already wearing it
        } else if (Rs2Inventory.hasItem("Amy's saw")) {
            Rs2Inventory.wield("Amy's saw");
            sleepUntil(() -> Rs2Equipment.isWearing("Amy's saw"), 1800);
        } else if (Rs2Bank.hasItem("Amy's saw")) {
            Rs2Bank.withdrawAndEquip("Amy's saw");
        } else {
            // Fallback to regular saw
            if (!Rs2Inventory.hasItem("Saw")) {
                Rs2Bank.withdrawOne("Saw");
                sleepUntil(() -> Rs2Inventory.hasItem("Saw"), 1800);
            }
        }

        // Imcando hammer OR Hammer
        if (Rs2Equipment.isWearing("Imcando hammer")) {
            // Already wearing it
        } else if (Rs2Inventory.hasItem("Imcando hammer")) {
            Rs2Inventory.wield("Imcando hammer");
            sleepUntil(() -> Rs2Equipment.isWearing("Imcando hammer"), 1800);
        } else if (Rs2Bank.hasItem("Imcando hammer")) {
            Rs2Bank.withdrawAndEquip("Imcando hammer");
        } else {
            // Fallback to regular hammer
            if (!Rs2Inventory.hasItem("Hammer")) {
                Rs2Bank.withdrawOne("Hammer");
                sleepUntil(() -> Rs2Inventory.hasItem("Hammer"), 1800);
            }
        }

        // Withdraw Plank Sack if enabled in config and missing
        if (plugin.getConfig().usePlankSack() && !Rs2Inventory.contains(ItemID.PLANK_SACK)) {
            if (Rs2Bank.hasItem(ItemID.PLANK_SACK)) {
                Rs2Bank.withdrawOne(ItemID.PLANK_SACK);
                sleepUntil(() -> Rs2Inventory.contains(ItemID.PLANK_SACK), 1800);
            }
        }
        // Handle teleport runes and Rune pouch
        boolean hasRunePouch = Rs2Inventory.hasRunePouch() || Rs2Bank.hasRunePouch();

        // Check total count across bank, inventory, and Rune pouch (if owned)
        int currentLaw = Rs2Inventory.count(ItemID.LAW_RUNE) + Rs2Bank.count(ItemID.LAW_RUNE) + (hasRunePouch ? Rs2RunePouch.getQuantity(Runes.LAW) : 0);
        int currentSteam = Rs2Inventory.count(ItemID.STEAM_RUNE) + Rs2Bank.count(ItemID.STEAM_RUNE) + (hasRunePouch ? Rs2RunePouch.getQuantity(Runes.STEAM) : 0);
        int currentDust = Rs2Inventory.count(ItemID.DUST_RUNE) + Rs2Bank.count(ItemID.DUST_RUNE) + (hasRunePouch ? Rs2RunePouch.getQuantity(Runes.DUST) : 0);

        if (currentLaw < 100 || currentSteam < 100 || currentDust < 100) {
            log("Missing required teleport runes! Law: %d, Steam: %d, Dust: %d", currentLaw, currentSteam, currentDust);
            Microbot.showMessage("Not enough teleport runes! Stopping plugin.");
            Microbot.stopPlugin(plugin);
            return;
        }

        if (hasRunePouch) {
            if (!Rs2Inventory.hasRunePouch()) {
                log("Withdrawing Rune pouch...");
                Rs2Bank.withdrawRunePouch();
                sleepUntil(Rs2Inventory::hasRunePouch, 1800);
            }

            if (Rs2Inventory.hasRunePouch()) {
                Rs2RunePouch.fullUpdate();
                Map<Runes, Integer> requiredRunes = new HashMap<>();

                int lawNeeded = Math.max(0, 1000 - Rs2RunePouch.getQuantity(Runes.LAW));
                int steamNeeded = Math.max(0, 1000 - Rs2RunePouch.getQuantity(Runes.STEAM));
                int dustNeeded = Math.max(0, 1000 - Rs2RunePouch.getQuantity(Runes.DUST));

                // Only load if any required rune is below 1000
                if (lawNeeded > 0 || steamNeeded > 0 || dustNeeded > 0) {
                    int lawToLoad = Rs2RunePouch.getQuantity(Runes.LAW) + Math.min(lawNeeded, Rs2Bank.count(ItemID.LAW_RUNE));
                    int steamToLoad = Rs2RunePouch.getQuantity(Runes.STEAM) + Math.min(steamNeeded, Rs2Bank.count(ItemID.STEAM_RUNE));
                    int dustToLoad = Rs2RunePouch.getQuantity(Runes.DUST) + Math.min(dustNeeded, Rs2Bank.count(ItemID.DUST_RUNE));

                    if (lawToLoad > 0) requiredRunes.put(Runes.LAW, lawToLoad);
                    if (steamToLoad > 0) requiredRunes.put(Runes.STEAM, steamToLoad);
                    if (dustToLoad > 0) requiredRunes.put(Runes.DUST, dustToLoad);

                    if (!requiredRunes.isEmpty()) {
                        log("Loading Law, Steam, and Dust runes into Rune Pouch...");
                        Rs2RunePouch.load(requiredRunes);
                    }
                }
            }
        } else {
            // Withdraw runes directly to inventory up to 1,000 of each
            int lawInv = Rs2Inventory.count(ItemID.LAW_RUNE);
            int steamInv = Rs2Inventory.count(ItemID.STEAM_RUNE);
            int dustInv = Rs2Inventory.count(ItemID.DUST_RUNE);

            if (lawInv < 1000) {
                int toWithdraw = Math.min(1000 - lawInv, Rs2Bank.count(ItemID.LAW_RUNE));
                if (toWithdraw > 0) {
                    Rs2Bank.withdrawX(ItemID.LAW_RUNE, toWithdraw);
                    sleepUntil(() -> Rs2Inventory.count(ItemID.LAW_RUNE) >= lawInv + toWithdraw, 1800);
                }
            }
            if (steamInv < 1000) {
                int toWithdraw = Math.min(1000 - steamInv, Rs2Bank.count(ItemID.STEAM_RUNE));
                if (toWithdraw > 0) {
                    Rs2Bank.withdrawX(ItemID.STEAM_RUNE, toWithdraw);
                    sleepUntil(() -> Rs2Inventory.count(ItemID.STEAM_RUNE) >= steamInv + toWithdraw, 1800);
                }
            }
            if (dustInv < 1000) {
                int toWithdraw = Math.min(1000 - dustInv, Rs2Bank.count(ItemID.DUST_RUNE));
                if (toWithdraw > 0) {
                    Rs2Bank.withdrawX(ItemID.DUST_RUNE, toWithdraw);
                    sleepUntil(() -> Rs2Inventory.count(ItemID.DUST_RUNE) >= dustInv + toWithdraw, 1800);
                }
            }
        }

        // If "Xeric's talisman" is found, withdraw and equip it
        if (Rs2Bank.hasItem("Xeric's talisman") || Rs2Inventory.hasItem("Xeric's talisman")) {
            withdrawAndEquipItem("Xeric's talisman");
        }
    }
}
