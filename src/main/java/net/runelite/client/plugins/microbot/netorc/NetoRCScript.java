package net.runelite.client.plugins.microbot.netorc;

import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.netorc.enums.RuneType;
import net.runelite.client.plugins.microbot.netorc.enums.State;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.netorc.enums.Teleports;
import net.runelite.client.plugins.microbot.shared.session.NetoBreakManager;
import net.runelite.client.plugins.microbot.shared.session.NetoRuntimeDisable;
import net.runelite.client.plugins.microbot.shared.session.NetoWorldHopManager;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectQueryable;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;

import javax.inject.Inject;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class NetoRCScript extends Script {
    private final NetoRCPlugin plugin;
    public static State state = State.BANKING;

    private enum WrathStep {
        MYTH_CAPE,
        MYTH_STATUE,
        CAVE,
        RUINS,
        ALTAR
    }

    private int lumbyElite = -1;

    private final WorldPoint feroxPoolWp = new WorldPoint(3129, 3636, 0);
    private final WorldPoint monasteryFairyRing = new WorldPoint(2656, 3230, 0);
    private final WorldPoint caveFairyRing = new WorldPoint(3447, 9824, 0);
    private final WorldPoint firstCaveExit = new WorldPoint(3460, 9813, 0);
    private final WorldPoint outsideBloodRuins74 = new WorldPoint(3555, 9783, 0);
    private final WorldPoint outsideBloodRuins93 = new WorldPoint(3543, 9772, 0);
    private final WorldPoint outsideBloodRuins73 = new WorldPoint(3558, 9779, 0);

	private volatile boolean forceDrinkAtFerox = false;
    private volatile boolean forceBankOnStart = true;
    private volatile int activeRunId = 0;
    private WrathStep wrathStep = WrathStep.MYTH_CAPE;
    private final ThreadLocal<Integer> scheduledRunId = new ThreadLocal<>();

    public static final int pureEss = 7936;
    public static final int feroxPool = 39651;
    public static final int monasteryRegion = 10290;
    public static final int bloodAltarRegion = 12875;

    public static final int guildSpiritTree = ObjectID.FARMING_SPIRIT_TREE_PATCH_5;
    private final WorldPoint guildSpiritTreeLoc = new WorldPoint(1252, 3749, 0);

    public static final int bloodRuins = ObjectID.BLOODTEMPLE_RUINED;
    public static final int bloodAltar = ObjectID.BLOOD_ALTAR;
    public static final int wrathRuins = ObjectID.WRATHTEMPLE_RUINED;
    public static final int wrathAltar = ObjectID.WRATH_ALTAR;

    public static final int activeBloodEssence = ItemID.BLOOD_ESSENCE_ACTIVE;
    public static final int inactiveBloodEssence = ItemID.BLOOD_ESSENCE_INACTIVE;
    public static final int bloodRune = ItemID.BLOODRUNE;
    public static final int wrathRune = ItemID.WRATHRUNE;
    public static final int colossalPouch = ItemID.RCU_POUCH_COLOSSAL;
    public static final int dramenStaff = ItemID.DRAMEN_STAFF;
    public static final int lunarStaff = ItemID.LUNAR_MOONCLAN_LIMINAL_STAFF;
    public static final int mythCape = ItemID.MYTHICAL_CAPE;
    public static final int dragonShield = ItemID.ANTIDRAGONBREATHSHIELD;

    @Inject
    public NetoRCScript(NetoRCPlugin plugin) {
        this.plugin = plugin;
    }

    @Inject
    private NetoRCConfig config;
    @Inject
    private Client client;
    @Inject
    private ClientThread clientThread;
    @Inject
    private NetoBreakManager breakManager;
    @Inject
    private NetoWorldHopManager worldHopManager;
    @Inject
    private NetoRuntimeDisable runtimeDisable;

    private Rs2TileObjectModel findObject(int id) {
        return new Rs2TileObjectQueryable().withId(id).first();
    }

    private boolean interactObject(int id, String action) {
        return new Rs2TileObjectQueryable().interact(id, action);
    }

    public boolean run() {
        if (mainScheduledFuture != null && !mainScheduledFuture.isDone()) {
            mainScheduledFuture.cancel(true);
        }

        int runId = startNewRun();
        Microbot.enableAutoRunOn = false;
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyRunecraftingSetup();
        Rs2AntibanSettings.actionCooldownChance = 0;
        Rs2Antiban.setActivity(Activity.CRAFTING_BLOODS_TRUE_ALTAR);
        Rs2Camera.setZoom(100);
        Rs2Camera.setPitch(305);
        Rs2Camera.setYaw(1024);
        sleepGaussian(700, 200);
        Microbot.log("Script has started");
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            scheduledRunId.set(runId);
            try {
                if (!isCurrentRun(runId)) return;
                if (runtimeDisable.updateRuntime(NetoRCPlugin.class)) return;
                if (breakManager.updateBreakState()) return;
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                if (!isCurrentRun(runId)) return;
                long startTime = System.currentTimeMillis();

                if (lumbyElite == -1) {
                    clientThread.invoke(() -> {
                        lumbyElite = Microbot.getClient().getVarbitValue(Varbits.DIARY_LUMBRIDGE_ELITE);
                    });
                    return;
                }

                if (Rs2Inventory.anyPouchUnknown()) {
                    if (Rs2Bank.isOpen()) {
                        Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
                        sleepUntil(() -> !Rs2Bank.isOpen(), 1200);
                    }
                    checkPouches();
                    return;
                }

                State initialState;
                do {
                    initialState = state;
                    switch (state) {
                        case BANKING:
                            handleBanking();
                            break;
                        case GOING_HOME:
                            if (config.usePoh()) {
                                handleGoingHome();
                            } else if (config.runeType() == RuneType.BLOOD && !config.usePoh()) {
                                handleArdyCloak();
                            } else if (config.runeType() == RuneType.WRATH) {
                                handleWrathWalking();
                                break;
                            }
                        case WALKING_TO:
                            handleWalking();
                            break;
                        case CRAFTING:
                            handleCrafting();
                            break;
                    }
                } while (state != initialState && isCurrentRun(runId) && Microbot.isLoggedIn());

                long endTime = System.currentTimeMillis();
                long totalTime = endTime - startTime;
                System.out.println("Total time for loop " + totalTime);

            } catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
                Microbot.log("Error in script" + ex.getMessage());
            } finally {
                scheduledRunId.remove();
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        invalidateCurrentRun();
        Rs2Antiban.resetAntibanSettings();
        super.shutdown();
        Microbot.log("Script has been stopped");
        //Rs2Player.logout();
    }

    public synchronized void resetToBanking() {
        state = State.BANKING;
        lumbyElite = -1;
        forceDrinkAtFerox = false;
        forceBankOnStart = true;
        wrathStep = WrathStep.MYTH_CAPE;
        breakManager.configure(config, "Neto RC");
        worldHopManager.configure(config, "Neto RC");
        runtimeDisable.configure(config, "Neto RC");
        breakManager.reset();
        worldHopManager.reset();
        runtimeDisable.reset();
    }

    private synchronized int startNewRun() {
        activeRunId++;
        resetToBanking();
        return activeRunId;
    }

    private synchronized void invalidateCurrentRun() {
        activeRunId++;
        resetToBanking();
    }

    private boolean isCurrentRun(int runId) {
        return runId == activeRunId;
    }

    private void setState(State nextState) {
        Integer runId = scheduledRunId.get();
        if (runId != null && !isCurrentRun(runId)) {
            return;
        }

        if (nextState == State.CRAFTING) {
            Rs2Antiban.setActivityIntensity(ActivityIntensity.HIGH);
        } else {
            Rs2Antiban.setActivityIntensity(ActivityIntensity.LOW);
        }

        state = nextState;
    }

    private void checkPouches() {
        Rs2Inventory.interact(colossalPouch, "Check");
        sleepGaussian(600, 200);
    }

    private void handleBanking() {
        int currentRegion = plugin.getMyWorldPoint().getRegionID();
        boolean forceBank = forceBankOnStart;

        if (forceBank) {
            if (!isBankingRegion(currentRegion)) {
                Microbot.log("Startup banking requested, teleporting to bank");
                handleBankTeleport();
                return;
            }
        } else if (!Rs2Inventory.allPouchesFull() && !Rs2Inventory.contains(pureEss)) {
            if (!isBankingRegion(currentRegion)) {
                Microbot.log("Not in banking region, teleporting");
                handleBankTeleport();
            }
        }

		if (plugin.isBreakHandlerEnabled()) {
			BreakHandlerScript.setLockState(true);
		}

        Rs2Tab.switchTo(InterfaceTab.INVENTORY);

		if (Rs2Inventory.hasDegradedPouch()) {
			Rs2Magic.repairPouchesWithLunar();
			sleepGaussian(600, 200);
			return;
		}

        if (Rs2Inventory.anyPouchUnknown()) {
            checkPouches();
        }

        if (!forceBank && Rs2Inventory.isFull() && Rs2Inventory.allPouchesFull() && Rs2Inventory.contains(pureEss)) {
            Microbot.log("We are full, skipping bank");
            setState(State.GOING_HOME);
            return;
        }
        if (!config.usePoh()) {
            handleFeroxRunEnergy();
        }

        while (!Rs2Bank.isOpen() && isRunning() && Rs2Bank.isNearBank(26) &&
                (forceBankOnStart || needsBankingSupplies())) {
            Microbot.log("Opening bank");
            Rs2Bank.openBank();
            sleepUntil(Rs2Bank::isOpen);
            sleepGaussian(700, 125);
        }

        if (forceBankOnStart && Rs2Bank.isOpen()) {
            forceBankOnStart = false;
        }

        if (config.runeType() == RuneType.WRATH) {
            handleWrathReqEquip();
            sleepGaussian(600, 125);
        }

        if (config.runeType() == RuneType.BLOOD) {
            if (!config.usePoh() && lumbyElite != 1) {
                if (!Rs2Equipment.isWearing(lunarStaff)) {
                    Microbot.log("Looking for and withdrawing lunar staff");
                    Rs2Bank.withdrawAndEquip(lunarStaff);
                    sleepUntil(() -> Rs2Equipment.isWearing(lunarStaff));
                } else if (!Rs2Equipment.isWearing(lunarStaff) && !Rs2Bank.hasItem(lunarStaff)) {
                    Microbot.log("No lunar staff found, withdrawing dramen staff");
                    Rs2Bank.withdrawAndEquip(dramenStaff);
                    sleepUntil(() -> Rs2Equipment.isWearing(dramenStaff));
                }
            }

            if (!config.usePoh() && !Rs2Equipment.isWearing("Ardougne cloak")) {
                Rs2Bank.withdrawAndEquip("Ardougne cloak");
                sleepGaussian(700, 200);
            }

            if (!Rs2Inventory.contains(activeBloodEssence) && !Rs2Inventory.contains(inactiveBloodEssence)) {
                if (!Rs2Bank.hasItem(activeBloodEssence)) {
                    Rs2Bank.withdrawItem(inactiveBloodEssence);
                    Microbot.log("Withdrawing blood essence");
                    sleepGaussian(900, 200);
                } else {
                    Rs2Bank.withdrawItem(activeBloodEssence);
                    sleepGaussian(900, 200);
                }
            }
        }
        int runecraftLevel = client.getRealSkillLevel(Skill.RUNECRAFT);
        if (runecraftLevel >= 99) {
            if (!Rs2Equipment.isWearing("Runecraft cape")) {
                Rs2Bank.withdrawAndEquip("Runecraft cape");
                sleepGaussian(700, 200);
            }
        } else {
            if (!Rs2Inventory.hasRunePouch()) {
                Rs2Bank.withdrawRunePouch();
                sleepGaussian(700, 200);
            }
        }

        if (config.usePoh()) {
            List<Teleports> bankTeleports = Arrays.asList(Teleports.CRAFTING_CAPE,
                    Teleports.FARMING_CAPE);
            boolean hasBankTeleport = false;
            for (Teleports bankTeleport : bankTeleports) {
                for (Integer bankTeleportID : bankTeleport.getItemIds()) {
                    if (Rs2Equipment.isWearing(bankTeleportID) || Rs2Inventory.contains(Teleports.HOUSE_TAB.getItemIds())) {
                        hasBankTeleport = true;
                        break;
                    } else if (!Rs2Equipment.isWearing(bankTeleportID) && Rs2Bank.hasItem(bankTeleportID)) {
                        Microbot.log("Withdrawing bank teleport " + bankTeleport.getName());
                        Rs2Bank.withdrawAndEquip(bankTeleportID);
                        sleepUntil(() -> Rs2Equipment.isWearing(bankTeleportID), 2400);
                        if (!Rs2Bank.hasItem(bankTeleportID)) {
                            Microbot.log("Withdrawing all house tabs");
                            Rs2Bank.withdrawAll(Arrays.toString(Teleports.HOUSE_TAB.getItemIds()));
                            sleepUntil(() -> Rs2Inventory.contains(Teleports.HOUSE_TAB.getItemIds()), 2400);
                        }
                    }
                }
                if (hasBankTeleport) {
                    Microbot.log("We have a bank teleport: " + bankTeleport.getName());
                    break;
                }
            }
        }

        for (int sailorsAmuletId : Teleports.SAILORS_AMULET.getItemIds()) {
            if (!Rs2Equipment.isWearing(sailorsAmuletId) && Rs2Bank.hasItem(sailorsAmuletId)) {
                Microbot.log("Withdrawing bank teleport " + Teleports.SAILORS_AMULET.getName());
                Rs2Bank.withdrawAndEquip(sailorsAmuletId);
                sleepUntil(() -> Rs2Equipment.isWearing(sailorsAmuletId));
                break;
            }
        }

        if (!Rs2Equipment.isWearing("Ring of dueling") && Rs2Bank.hasItem("Ring of dueling")) {
            Microbot.log("Withdrawing ring of dueling");
            Rs2Bank.withdrawAndEquip(2552);
            sleepUntil(() -> Rs2Equipment.isWearing("Ring of dueling"));
        }

        handleFillPouch();

        if (Rs2Bank.isOpen() && Rs2Inventory.allPouchesFull() && Rs2Inventory.isFull()) {
            Microbot.log("We are full, lets go");
            Rs2Bank.closeBank();
            sleepUntil(() -> !Rs2Bank.isOpen(), 1200);
            if (config.runeType() == RuneType.BLOOD) {
                if (Rs2Inventory.contains(inactiveBloodEssence)) {
                    Rs2Inventory.interact(inactiveBloodEssence, "Activate");
                    Microbot.log("Activating blood essence");
                    sleepGaussian(700, 200);
                }
            }

            if (worldHopManager.tryHopIfDue(this::isRunning).isAttempted()) {
                return;
            }

            if (config.runeType() == RuneType.WRATH && config.usePoh()) {
                if (Rs2Player.getRunEnergy() > 45) {
                    handleWrathWalking();
                }
            } else {
                setState(State.GOING_HOME);
            }
        }
    }

    private void handleFillPouch() {
        while (!Rs2Inventory.allPouchesFull() || !Rs2Inventory.isFull() && isRunning()) {
            Microbot.log("Pouches are not full yet");
            if (Rs2Bank.isOpen()) {
                if (Rs2Inventory.contains(bloodRune)) {
                    Rs2Bank.depositAll(bloodRune);
                    sleepGaussian(300, 100);
                }
                if (Rs2Inventory.contains(wrathRune)) {
                    Rs2Bank.depositAll(wrathRune);
                    sleepGaussian(300, 100);
                }
                Rs2Bank.withdrawAll(pureEss);
                sleepUntil(Rs2Inventory::isFull);
                Rs2Inventory.fillPouches();
                sleepUntilOnClientThread(() -> !Rs2Inventory.isFull());
            }
            if (!Rs2Inventory.isFull()) {
                Rs2Bank.withdrawAll(pureEss);
                sleepUntil(Rs2Inventory::isFull);
            }
        }
    }

    private void handleFeroxRunEnergy() {
		if (forceDrinkAtFerox || Rs2Player.getRunEnergy() <= 15 || Rs2Player.getHealthPercentage() <= 20) {
			Microbot.log("We are thirsty...let us Drink");
            forceDrinkAtFerox = true;
            if (plugin.getMyWorldPoint().distanceTo(feroxPoolWp) > 5) {
                Microbot.log("Walking to Ferox pool");
                Rs2Walker.walkTo(feroxPoolWp);
                sleepUntil(() -> plugin.getMyWorldPoint().distanceTo(feroxPoolWp) < 5);
            }

            if (plugin.getMyWorldPoint().distanceTo(feroxPoolWp) < 5) {
                Microbot.log("Interacting with the Ferox pool");
                interactObject(feroxPool, "Drink");
            }
            sleepUntil(() -> (!Rs2Player.isInteracting()) && !Rs2Player.isAnimating() && Rs2Player.getRunEnergy() > 90);
            sleepGaussian(1100, 200);
			forceDrinkAtFerox = false;
        }
    }

    private void handleArdyCloak() {
        Teleports ardyCloakTeleport = Teleports.ARDOUGNE_CLOAK;

        if (plugin.isBreakHandlerEnabled()) {
            BreakHandlerScript.setLockState(true);
        }

        for (Integer itemId : ardyCloakTeleport.getItemIds()) {
            if (Rs2Equipment.isWearing(itemId)) {
                Microbot.log("Using Ardy cloak");
                Rs2Equipment.interact(itemId, ardyCloakTeleport.getInteraction());
                Microbot.log("Waiting for region " + monasteryRegion);
                sleepUntil(() -> plugin.getMyWorldPoint().getRegionID() == (monasteryRegion));
                sleepGaussian(1100, 200);
            }
        }

        if (plugin.getMyWorldPoint().distanceTo(monasteryFairyRing) > 7) {
            Microbot.log("Walking to monastery fairy ring");
            Rs2Walker.walkTo(monasteryFairyRing);
            sleepUntil(() -> plugin.getMyWorldPoint().distanceTo(monasteryFairyRing) < 7);
            sleepGaussian(900, 200);
        }

        var fairyRing = new Rs2TileObjectQueryable().withNameContains("fairy").first();

        if (plugin.getMyWorldPoint().distanceTo(monasteryFairyRing) < 7) {
            if (fairyRing == null) {
                Microbot.log("Unable to find fairies, resetting from bank to retry");
                setState(State.BANKING);
                return;
            } else {
                Microbot.log("Interacting with fairies");
                interactObject(fairyRing.getId(), "Last-destination (DLS)");
                sleepUntil(() -> plugin.getMyWorldPoint().equals(caveFairyRing));
            }
        }
        setState(State.WALKING_TO);
    }

    private void handleFarmingCape() {
        Teleports farmingCapeTeleport = Teleports.FARMING_CAPE;
        if (config.usePoh()) {
            if (Rs2Equipment.isWearing(Arrays.toString(farmingCapeTeleport.getItemIds()))) {
                if (plugin.getMyWorldPoint().getRegionID() != 4922) {
                    Rs2Equipment.interact(Arrays.toString(farmingCapeTeleport.getItemIds()),
                            farmingCapeTeleport.getInteraction());
                    sleepUntil(() -> plugin.getMyWorldPoint().getRegionID() == 4922);
                    sleepGaussian(1100, 200);
                }
                if (plugin.getMyWorldPoint().distanceTo(guildSpiritTreeLoc) > 10) {
                    Rs2Walker.walkTo(guildSpiritTreeLoc);
                } else {
                    interactObject(guildSpiritTree, "Travel");
                    sleepUntil(() -> Rs2Widget.isWidgetVisible(187, 3), 10000);
                    sleepGaussian(1100, 200);

                    Widget parent = client.getWidget(187, 3);
                    if (parent != null && parent.getChildren() != null) {
                        for (Widget child : parent.getChildren()) {
                            if (child != null && child.getText() != null && child.getText().toLowerCase().contains("house")) {
                                Microbot.log("Found house widgetId");
                                Rs2Widget.clickWidget(child);
                                sleepUntil(() -> !Rs2Player.isAnimating(), 5000);
                                sleepUntil(() -> Microbot.getClient().getTopLevelWorldView() != null, 5000);
                                sleepGaussian(1300, 200);
                                break;
                            }
                        }
                    }
                    if (Rs2Player.getRunEnergy() < 45) {
                        sleepGaussian(700, 200);
                        Microbot.log("We are thirsty..let us Drink");
                        List<Integer> poolObjectIds = Arrays.asList(29241, 29240, 29239, 29238, 29237);
                        poolObjectIds.stream().filter(id -> findObject(id) != null).findFirst()
                                .ifPresent(objectId -> {
                                    interactObject(objectId, "Drink");
                                    sleepUntil(() -> !Rs2Player.isInteracting() && Rs2Player.getRunEnergy() > 90);
                                });
                    }
                    if (Rs2Player.getRunEnergy() > 45) {
                        if (config.runeType() == RuneType.BLOOD) {
                            sleepGaussian(700, 200);
                            Microbot.log("Looking for fairies");
                            handlePohFairyRing();
                        }
                    }
                }
            }
        }
    }

    private void handleWrathWalking() {
        if (plugin.isBreakHandlerEnabled()) {
            BreakHandlerScript.setLockState(true);
        }

        if (Rs2Bank.isOpen()) {
            Rs2Bank.closeBank();
        }

        WrathStep initialStep;
        do {
            initialStep = wrathStep;
            switch (wrathStep) {
                case MYTH_CAPE:
                    if (Rs2Inventory.contains(mythCape)) {
                        Rs2Inventory.interact(mythCape, "Teleport");
                        sleepGaussian(700, 50); // 600 to 800 ms
                        sleepUntil(() -> !Rs2Player.isAnimating(), 5000);
                        sleepUntilOnClientThread(() -> findObject(31626) != null, 5000); // Wait for Myth Statue
                        wrathStep = WrathStep.MYTH_STATUE;
                    }
                    break;
                case MYTH_STATUE:
                    var statue = findObject(31626);
                    if (statue != null && !Rs2Player.isAnimating()) {
                        sleepGaussian(150, 25); // 100 to 200 ms
                        interactObject(statue.getId(), "Teleport");
                        sleepUntilOnClientThread(() -> findObject(31807) != null, 5000); // Wait for Cave
                        wrathStep = WrathStep.CAVE;
                    }
                    break;
                case CAVE:
                    var cave = findObject(31807);
                    if (cave != null) {
                        sleepGaussian(150, 25); // 100 to 200 ms
                        interactObject(cave.getId(), "Enter");
                        sleepUntilOnClientThread(() -> findObject(wrathRuins) != null, 20000); // Wait for Ruins
                        wrathStep = WrathStep.RUINS;
                    }
                    break;
                case RUINS:
                    var ruins = findObject(wrathRuins);
                    if (ruins != null) {
                        sleepGaussian(1250, 250); // 500 to 2000 ms
                        interactObject(ruins.getId(), "Enter");
                        sleepUntilOnClientThread(() -> findObject(wrathAltar) != null, 5000); // Wait for Altar
                        wrathStep = WrathStep.ALTAR;
                    }
                    break;
                case ALTAR:
                    var altar = findObject(wrathAltar);
                    if (altar != null) {
                    sleepGaussian(150, 25); // 100 to 200 ms
                        interactObject(altar.getId(), "Craft-rune");
                        wrathStep = WrathStep.MYTH_CAPE;
                        setState(State.CRAFTING);
                        return;
                    }
                    break;
            }
        } while (wrathStep != initialStep);
    }

    private boolean isBankingRegion(int currentRegion) {
        return Teleports.CRAFTING_CAPE.matchesRegion(currentRegion)
                || Teleports.FEROX_ENCLAVE.matchesRegion(currentRegion)
                || Teleports.FARMING_CAPE.matchesRegion(currentRegion)
                || Teleports.SAILORS_AMULET.matchesRegion(currentRegion)
                || Teleports.CASTLE_WARS.matchesRegion(currentRegion);
    }

    private boolean needsBankingSupplies() {
        return !Rs2Inventory.allPouchesFull()
                || !Rs2Inventory.contains(colossalPouch)
                || !Rs2Inventory.contains(pureEss);
    }

    private void handleWrathReqEquip() {
        if (!Rs2Equipment.isWearing(dragonShield)) {
            Microbot.log("Withdrawing " + dragonShield);
            Rs2Bank.withdrawAndEquip(dragonShield);
            sleepUntil(() -> Rs2Equipment.isWearing(dragonShield));
            sleepGaussian(900, 200);
        }
        if (!Rs2Inventory.contains(mythCape)) {
            Microbot.log("Withdrawing " + mythCape);
            Rs2Bank.withdrawItem(mythCape);
            sleepUntil(() -> Rs2Inventory.contains(mythCape));
            sleepGaussian(900, 200);
        }
    }

    private void handleGoingHome() {
        if (plugin.isBreakHandlerEnabled()) {
            BreakHandlerScript.setLockState(true);
        }

        if (config.runeType() == RuneType.WRATH && Rs2Player.getRunEnergy() > 90) {
            setState(State.WALKING_TO);
        }
        else if (config.runeType() == RuneType.WRATH
                && Rs2Player.getRunEnergy() < 45
                || Rs2Player.getHealthPercentage() < 50) {

            Teleports homeTeleports = Teleports.CONSTRUCTION_CAPE;
            GameObject pohPortal = plugin.getPohPortal();

            if (!Rs2Inventory.contains(homeTeleports.getItemIds())) {
                Microbot.log("Con cape not found");
                homeTeleports = Teleports.HOUSE_TAB;
            }
            for (Integer itemId : homeTeleports.getItemIds()) {
                if (Rs2Inventory.contains(itemId)) {
                    Microbot.log("Using " + homeTeleports.getName());
                    Rs2Inventory.interact(itemId, homeTeleports.getInteraction());
                    sleepGaussian(1100, 200);
                    sleepUntil(() -> !Rs2Player.isAnimating(), 5000);
                    sleepUntil(() -> Microbot.getClient().getTopLevelWorldView() != null, 5000);
                    sleepGaussian(1300, 200);

                    if (pohPortal != null) {
                        Microbot.log("Poh portal found, we are home");
                    }
                    Microbot.log("We should be in poh fully loaded");
                }
            }

            if (Rs2Player.getRunEnergy() < 45) {
                sleepGaussian(700, 200);
                Microbot.log("We are thirsty..let us Drink");
                List<Integer> poolObjectIds = Arrays.asList(29241, 29240, 29239, 29238, 29237);
                poolObjectIds.stream().filter(id -> findObject(id) != null).findFirst()
                        .ifPresent(objectId -> {
                            interactObject(objectId, "Drink");
                            sleepUntil(() -> !Rs2Player.isInteracting() && Rs2Player.getRunEnergy() > 90);
                        });
            }

            if (Rs2Player.getRunEnergy() > 45) {
                if (config.runeType() == RuneType.BLOOD) {
                    sleepGaussian(700, 200);
                    handlePohFairyRing();
                }
            }
        }

        if (config.runeType() == RuneType.BLOOD) {

            if (Rs2Equipment.isWearing(Arrays.toString(Teleports.FARMING_CAPE.getItemIds()))) {
                handleFarmingCape();
            }
            Teleports homeTeleports = Teleports.CONSTRUCTION_CAPE;
            GameObject pohPortal = plugin.getPohPortal();

            if (!Rs2Inventory.contains(homeTeleports.getItemIds())) {
                Microbot.log("Con cape not found");
                homeTeleports = Teleports.HOUSE_TAB;
            }
            for (Integer itemId : homeTeleports.getItemIds()) {
                if (Rs2Inventory.contains(itemId)) {
                    Microbot.log("Using " + homeTeleports.getName());
                    Rs2Inventory.interact(itemId, homeTeleports.getInteraction());
                    sleepGaussian(1100, 200);
                    sleepUntil(() -> !Rs2Player.isAnimating(), 5000);
                    sleepUntil(() -> Microbot.getClient().getTopLevelWorldView() != null, 5000);
                    sleepGaussian(1300, 200);

                    if (pohPortal != null) {
                        Microbot.log("Poh portal found, we are home");
                    }
                    Microbot.log("We should be in poh fully loaded");
                }
            }

            if (Rs2Player.getRunEnergy() < 45) {
                sleepGaussian(700, 200);
                Microbot.log("We are thirsty..let us Drink");
                List<Integer> poolObjectIds = Arrays.asList(29241, 29240, 29239, 29238, 29237);
                poolObjectIds.stream().filter(id -> findObject(id) != null).findFirst()
                        .ifPresent(objectId -> {
                            interactObject(objectId, "Drink");
                            sleepUntil(() -> !Rs2Player.isInteracting() && Rs2Player.getRunEnergy() > 90);
                        });
            }

            if (Rs2Player.getRunEnergy() > 45) {
                if (config.runeType() == RuneType.BLOOD) {
                    sleepGaussian(700, 200);
                    handlePohFairyRing();
                }
            }
        }
    }

    private void handlePohFairyRing() {
        if (findObject(ObjectID.POH_FAIRY_RING) != null) {
            interactObject(ObjectID.POH_FAIRY_RING, "Last-destination (DLS)");
            Microbot.log("Using fairy ring");
            Rs2Player.waitForAnimation(1200);
            sleepUntil(() -> plugin.getMyWorldPoint().equals(caveFairyRing), 1200);
            setState(State.WALKING_TO);
        } else {
            var pohTreeRing = new Rs2TileObjectQueryable().withNameContains("spirit").first();
            if (pohTreeRing != null) {
                interactObject(pohTreeRing.getId(), "Ring-last-destination (DLS)");
                Microbot.log("Using fairy tree");
                Rs2Player.waitForAnimation();
                sleepUntil(() -> plugin.getMyWorldPoint().equals(caveFairyRing));
            } else {
                Microbot.log("Unable to find fairy ring, resetting to banking for a retry");
                setState(State.BANKING);
            }
        }

        if (Rs2Player.getWorldLocation().equals(caveFairyRing)) {
            setState(State.WALKING_TO);
        }
    }


    private void handleWalking() {
        if (plugin.isBreakHandlerEnabled()) {
            BreakHandlerScript.setLockState(true);
        }

        if (config.runeType() == RuneType.WRATH) {
            handleWrathWalking();
        }

        if (config.runeType() == RuneType.BLOOD) {
            Microbot.log("Current location after waiting: " + plugin.getMyWorldPoint());
            if (plugin.getMyWorldPoint().equals(caveFairyRing)) {
                sleepGaussian(900, 200);
                interactObject(16308, "Enter");
                sleepUntil(() -> Rs2Player.getWorldLocation().equals(firstCaveExit), 1200);
                sleepGaussian(900, 200);
            }

            if (plugin.getMyWorldPoint().equals(firstCaveExit) &&
                    Rs2Player.getRealSkillLevel(Skill.AGILITY) >= 93) {
                Microbot.log("Walking to blood ruins " +
                        outsideBloodRuins93);
                Rs2Walker.walkTo(outsideBloodRuins93);
                sleepUntil(() -> Rs2Player.getWorldLocation().equals(outsideBloodRuins93), 1200);
            }

            if (plugin.getMyWorldPoint().equals(firstCaveExit) &&
                    Rs2Player.getRealSkillLevel(Skill.AGILITY) < 93 && Rs2Player.getRealSkillLevel(Skill.AGILITY) >= 74) {
                Microbot.log("Walking to ruins: " + outsideBloodRuins74);
                Rs2Walker.walkTo(outsideBloodRuins74);
                sleepUntil(() -> plugin.getMyWorldPoint().equals(outsideBloodRuins74), 1200);
            }

            var ruins = findObject(bloodRuins);

            if (plugin.getMyWorldPoint().equals(firstCaveExit) && Rs2Player.getRealSkillLevel(Skill.AGILITY) < 74) {
                Microbot.log("Walking to ruins: " + outsideBloodRuins73);
                Rs2Walker.walkTo(outsideBloodRuins73);
                sleepUntil(() -> Rs2Player.distanceTo(new WorldPoint(3560, 9780, 0)) < 5);
            }

            if (ruins != null && plugin.getMyWorldPoint().getRegionID() == 14232
                    && !Rs2Player.isMoving() && !Rs2Player.isAnimating() &&
                    Rs2Player.distanceTo(new WorldPoint(3560, 9780, 0)) < 18) {
                setState(State.CRAFTING);
            }
        }
    }

    private void handleCrafting() {
        if (plugin.isBreakHandlerEnabled()) {
            BreakHandlerScript.setLockState(true);
        }

        if (config.runeType() == RuneType.BLOOD) {
            interactObject(bloodRuins, "Enter");
            sleepUntil(() -> !Rs2Player.isAnimating() && plugin.getMyWorldPoint().getRegionID() == bloodAltarRegion);
            sleepGaussian(700, 200);
            interactObject(bloodAltar, "Craft-rune");
            Rs2Player.waitForXpDrop(Skill.RUNECRAFT);
            plugin.updateXpGained();
            handleEmptyPouch();

            while (plugin.getMyWorldPoint().getRegionID() == bloodAltarRegion && isRunning()) {
                if (Rs2Inventory.allPouchesEmpty() && !Rs2Inventory.contains("Pure essence")) {
                    Microbot.log("We are in altar region and out of p ess, banking...");
                    handleBankTeleport();
                    sleepGaussian(500, 200);
                }
            }
            setState(State.BANKING);
        }

        if (config.runeType() == RuneType.WRATH) {
            sleepUntilOnClientThread(() -> Rs2Inventory.hasItem(ItemID.WRATHRUNE));
            handleEmptyPouch();
        }

        worldHopManager.recordCompletedTrip();

        if (pauseForBreakAfterCrafting()) {
            setState(State.BANKING);
            return;
        }

        setState(State.BANKING);
    }

    private boolean pauseForBreakAfterCrafting() {
        if (!isBankingRegion(plugin.getMyWorldPoint().getRegionID())) {
            handleBankTeleport();
            sleepUntil(() -> isBankingRegion(plugin.getMyWorldPoint().getRegionID()), 5000);
        }

        if (!isBankingRegion(plugin.getMyWorldPoint().getRegionID())) {
            if (plugin.isBreakHandlerEnabled()) {
                BreakHandlerScript.setLockState(true);
            }
            return false;
        }

        if (breakManager.tryStartBreakAtSafePoint()) {
            return true;
        }

        if (!plugin.isBreakHandlerEnabled()) {
            return false;
        }

        BreakHandlerScript.setLockState(false);
        if (BreakHandlerScript.isBreakActive() || BreakHandlerScript.breakIn <= 0) {
            return true;
        }

        BreakHandlerScript.setLockState(true);
        return false;
    }

    private void handleEmptyPouch() {
        while (!Rs2Inventory.allPouchesEmpty() && isRunning()) {
            Microbot.log("Pouches are not empty. Crafting more");
            Rs2Inventory.emptyPouches();

//            // Determine the altar to interact with and hover the mouse over it
//            var altar = config.runeType() == RuneType.BLOOD
//                    ? findObject(bloodAltar)
//                    : findObject(wrathAltar);
//            if (altar != null) {
//                Rs2GameObject.hoverOverObject(altar);
//            }

            sleepUntil(() -> Rs2Inventory.contains(pureEss));

            if (config.runeType() == RuneType.BLOOD) {
                interactObject(bloodAltar, "Craft-rune");
            }
            if (config.runeType() == RuneType.WRATH) {
                interactObject(wrathAltar, "Craft-rune");
            }
            Rs2Player.waitForXpDrop(Skill.RUNECRAFT);
            plugin.updateXpGained();
        }
    }

    private void handleBankTeleport() {
        boolean needRefill = (forceDrinkAtFerox || Rs2Player.getRunEnergy() <= 15 || Rs2Player.getHealthPercentage() <= 20);

        if (!needRefill) {
            for (int craftingCapeId : Teleports.CRAFTING_CAPE.getItemIds()) {
                if (Rs2Inventory.contains(craftingCapeId)) {
                    Microbot.log("Using: " + Teleports.CRAFTING_CAPE.getName());
                    Rs2Inventory.interact(craftingCapeId, Teleports.CRAFTING_CAPE.getInteraction());
                    sleepUntil(() -> Teleports.CRAFTING_CAPE.matchesRegion(plugin.getMyWorldPoint().getRegionID()));
                    sleepGaussian(600, 200);
                    return;
                }
            }
        }

        Rs2Tab.switchTo(InterfaceTab.INVENTORY);
        sleepGaussian(1300, 200);
        List<Teleports> bankTeleport = needRefill
                ? Arrays.asList(
                Teleports.FEROX_ENCLAVE,
                Teleports.CRAFTING_CAPE,
                Teleports.FARMING_CAPE)
                : Arrays.asList(
                Teleports.CRAFTING_CAPE,
                Teleports.FARMING_CAPE,
                Teleports.SAILORS_AMULET,
                Teleports.CASTLE_WARS
        );
        boolean teleportUsed = false;

        for (Teleports teleport : bankTeleport) {
            for (Integer bankTeleportsId : teleport.getItemIds()) {
                if (Rs2Equipment.isWearing(bankTeleportsId)) {
                    Microbot.log("Using: " + teleport.getName());
                    Rs2Equipment.interact(bankTeleportsId, teleport.getInteraction());
                    sleepUntil(() -> teleport.matchesRegion(plugin.getMyWorldPoint().getRegionID()));
                    sleepGaussian(600, 200);
                    if (teleport == Teleports.FEROX_ENCLAVE) {
                        forceDrinkAtFerox = true;
                        handleFeroxRunEnergy();
                    }
                    teleportUsed = true;
                    break;
                }
            }
            if (teleportUsed) break;
        }
    }
}
